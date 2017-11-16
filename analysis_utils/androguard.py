import datetime
import re
import time
from analysis_utils import adbutils, droidmate
from androguard.core.bytecodes import apk
import os
import glob
import shutil


class Androguard:

    def __init__(self, target_apk, path):
        self.apk = target_apk
        self.output = path
        self.droidmate = True
        self.message = ""
        self.timeout = 180
        self.activity_count = 0
        self.provider_count = 0
        self.service_count = 0
        self.receiver_count = 0

        self.activity_max = None
        self.provider_max = None
        self.service_max = None
        self.receiver_max = None

    @staticmethod
    def get_package(path):
        androlyzed_apk = apk.APK(path)
        package = androlyzed_apk.get_package()
        return package

    @staticmethod
    def generate_pid(path, package):
        androlyzed_apk = apk.APK(path)
        main_activity = androlyzed_apk.get_main_activity()
        cmd = 'am start -n \"' + package + '/' + main_activity + '\"'
        adbutils.adb_shell(cmd, device="emulator-5554")
        return Androguard.get_pid(package)

    def explore(self, with_droidmate):
        pids = []
        try:
            self.log("Starting exploration")
            # pass target apk to droidmate
            apk_file = os.getcwd() + "/analysis_utils/droidmate/dev/droidmate/apks"
            files = glob.glob(apk_file + "/*")
            for f in files:
                os.remove(f)
            shutil.copy2(self.apk, apk_file)
            if self.droidmate:
                # prepare connection to droidmate
                self.log("Setting up Droidmate")
                droidmate_conn = droidmate.Droidmate()
                try:
                    droidmate_conn.send_go()
                except RuntimeError as err:
                    self.message = str(err)
                    self.log(self.message)
                    self.log("continuing without Droidmate")
                    self.droidmate = False
                    droidmate_conn = None
            else:
                droidmate_conn = None
            
            androlyzed_apk = apk.APK(self.apk)
            self.log("Analyzing " + androlyzed_apk.get_app_name())

            # get package_name for further steps
            package_name = androlyzed_apk.get_package()
            self.log("#####PACKAGE#####")
            self.log(package_name)

            # get name of main activity
            main_activity = androlyzed_apk.get_main_activity()
            self.log("#####MAIN_ACTIVITY#####")
            self.log(main_activity)
            # start the main activity to create an active process
            cmd = 'am start -n \"' + package_name + '/' + main_activity + '\"'
            self.log(cmd)
            self.log(adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)[1])
            time.sleep(3)
            pid = self.get_pid(package_name)
            if pid not in pids:
                pids.append(pid)

            # get names of services
            service_list = androlyzed_apk.get_services()
            self.service_max = len(service_list)
            self.log("#####SERVICES#####")
            for service in service_list:
                service = str(service, "ascii")
                self.log(service)
                self.explore_service(service, package_name, droidmate_conn)
                # start the main activity to reset the app
                cmd = 'am start -n \"' + package_name + '/' + main_activity + '\"'
                adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)
                pid = self.get_pid(package_name)
                if pid not in pids:
                    pids.append(pid)
                self.service_count += 1

            # get names of broadcast receivers
            receiver_list = androlyzed_apk.get_receivers()
            self.receiver_max = len(receiver_list)
            self.log("#####RECEIVERS#####")
            for receiver in receiver_list:
                receiver = str(receiver, "ascii")
                self.log(receiver)
                # we need the corresponding intent filters
                intent_filter = androlyzed_apk.get_intent_filters("receiver", receiver)
                intent_filter = re.sub('\{|\}|\s|\n|\'|\[|\]', '', str(intent_filter))
                self.log(intent_filter)
                filters = intent_filter.split(',')
                action = ''
                category = ''
                data = ''
                for f in filters:
                    arg = f.split(':')
                    if arg[0] == 'action':
                        action = arg[1]
                    elif arg[0] == 'category':
                        category = arg[1]
                    elif arg[0] == 'data':
                        data = arg[1]
                self.explore_receiver(receiver, package_name, action, category, data, droidmate_conn)
                self.receiver_count += 1
                # start the main activity to reset the app
                cmd = 'am start -n \"' + package_name + '/' + main_activity + '\"'
                adbutils.adb_shell(cmd, device="emulator-5554")
                pid = self.get_pid(package_name)
                if pid not in pids:
                    pids.append(pid)

            # get names of content providers
            # the information that androguard returns when scanning for content providers is not useful for us
            providers = adbutils.adb_shell('dumpsys package providers | grep '
                                           + package_name, device="emulator-5554")[1]
            provider_list = providers.split('\n')
            self.provider_max = len(provider_list)
            self.log("#####PROVIDERS#####")
            for provider in provider_list:
                if '[' in provider and ']:' in provider:
                    provider = re.sub('\[|\]|\s|:', '', provider)
                    self.log(provider)
                    self.explore_provider(provider)
                    self.provider_count += 1

            # get names of activities
            activities = androlyzed_apk.get_activities()
            self.activity_max = len(activities)
            self.log("#####ACTIVITIES#####")
            for activity in activities:
                activity = str(activity, "ascii")
                self.log(activity)
                pid = self.explore_activity(activity, package_name, droidmate_conn)
                self.activity_count += 1
                if pid not in pids:
                    pids.append(pid)

            # stop droidmate
            if self.droidmate:
                droidmate_conn.send_stop()
            else:
                self.message = self.message + "\n\n"

            self.log_success()
            self.log(self.message)
        except TimeoutError:
            self.log_success()
            self.log(self.message)
            raise TimeoutError(self.message, pids)
        except KeyboardInterrupt:
            self.log_success()
            self.log(self.message)
            raise KeyboardInterrupt(self.message, pids)

        return pids, self.droidmate, self.message

    def log_success(self):
        if self.activity_max is not None:
            self.message += "Activities explored: " + str(self.activity_count) + "/" + str(self.activity_max) + "\n"
        if self.receiver_max is not None:
            self.message += "Receivers explored: " + str(self.receiver_count) + "/" + str(self.receiver_max) + "\n"
        if self.service_max is not None:
            self.message += "Services explored: " + str(self.service_count) + "/" + str(self.service_max) + "\n"
        if self.provider_max is not None:
            self.message += "Providers explored: " + str(self.provider_count) + "/" + str(self.provider_max) + "\n"

    @staticmethod
    def get_pid(package):
        cmd = 'ps | grep ' + package
        check = False
        output = ""
        for i in range(0, 60):
            time.sleep(1)
            output = adbutils.adb_shell(cmd, device="emulator-5554", timeout=180)[1]
            if output != "":
                check = True
                break
        if not check:
            raise TimeoutError
        
        output = re.sub('\s+', ',', output)
        try:
            pid = output.split(',')[1]
        except IndexError:
            return None
        return pid

    def explore_provider(self, provider):
        # we don't know the exact paths so we simply try to execute a query on the provider itself
        cmd = 'content query --uri content://' + provider + ' --user 0'
        self.log(adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)[1])

    def explore_receiver(self, receiver, package, action, category, data, droidmate_conn):
        cmd = 'am broadcast -n \"' + package + '/' + receiver + '\"'
        if action != '':
            cmd = cmd + ' -a ' + action
        if category != '':
            cmd = cmd + ' -c ' + category
        if data != '':
            cmd = cmd + ' -d ' + data
        self.log(cmd)
        self.log(adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)[1])

        # DroidMate exploration
        if self.droidmate:
            try:
                droidmate_conn.send_go()
            except RuntimeError as err:
                self.message = str(err)
                self.log(self.message)
                self.log("continuing without Droidmate")
                self.droidmate = False
        else:
            time.sleep(3)

    def explore_service(self, service, package, droidmate_conn):
        cmd = 'am startservice -n \"' + package + '/' + service + '\"'
        self.log(cmd)
        self.log(adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)[1])
        # DroidMate exploration
        if self.droidmate:
            try:
                droidmate_conn.send_go()
            except RuntimeError as err:
                self.message = str(err)
                self.log("continuing without Droidmate")
                self.droidmate = False
        else:
            time.sleep(3)

    def explore_activity(self, activity, package, droidmate_conn):
        cmd = 'am start -n \"' + package + '/' + activity + '\"'
        self.log(cmd)
        self.log(adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)[1])
        # DroidMate exploration
        pid = self.get_pid(package)
        if self.droidmate:
            try:
                droidmate_conn.send_go()
            except RuntimeError as err:
                self.message = str(err)
                self.log("continuing without Droidmate")
                self.droidmate = False
        else:
            time.sleep(3)

        # force stop of app in case of errors
        cmd = 'am force-stop ' + package
        self.log(adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)[1])
        return pid

    def log(self, log_string):
        f = open(self.output, "a")
        f.write('[' + str(datetime.datetime.now()) + '] ' + log_string + "\n")
        f.close()
