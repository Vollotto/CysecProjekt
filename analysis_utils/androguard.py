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
        self.path_to_apk = target_apk
        self.apk = apk.APK(target_apk)
        self.main_activity = self.apk.get_main_activity()
        self.package = self.apk.get_package()

        self.activities = self.apk.get_activities()
        self.services = self.apk.get_services()
        self.receivers = self.apk.get_receivers()
        self.providers = self.apk.get_providers()

        self.activity_max = len(self.activities)
        self.service_max = len(self.services)
        self.receiver_max = len(self.receivers)
        self.provider_max = len(self.providers)

        self.output = path
        self.droidmate = True
        self.message = ""
        self.timeout = 180

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

    def explore(self, with_droidmate):
        pids = []
        try:
            # pass target apk to droidmate
            apk_file = os.getcwd() + "/analysis_utils/droidmate/dev/droidmate/apks"
            files = glob.glob(apk_file + "/*")
            for f in files:
                os.remove(f)
            shutil.copy2(self.path_to_apk, apk_file)
            if self.droidmate:
                # prepare connection to droidmate
                droidmate_conn = droidmate.Droidmate()
                try:
                    droidmate_conn.send_go()
                except RuntimeError as err:
                    self.message = str(err)
                    self.droidmate = False
                    droidmate_conn = None
            else:
                droidmate_conn = None
            # start the main activity to create an active process
            cmd = 'am start -n \"' + self.package + '/' + self.main_activity + '\"'
            time.sleep(3)
            pid = self.get_pid(self.package)
            if pid not in pids:
                pids.append(pid)
            for service in self.services:
                service = str(service, "ascii")
                self.explore_service(service, self.package, droidmate_conn)
                # start the main activity to reset the app
                cmd = 'am start -n \"' + self.package + '/' + self.main_activity + '\"'
                adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)
                pid = self.get_pid(self.package)
                if pid not in pids:
                    pids.append(pid)

            for receiver in self.receivers:
                receiver = str(receiver, "ascii")
                # we need the corresponding intent filters
                intent_filter = self.apk.get_intent_filters("receiver", receiver)
                intent_filter = re.sub('\{|\}|\s|\n|\'|\[|\]', '', str(intent_filter))
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
                self.explore_receiver(receiver, self.package, action, category, data, droidmate_conn)
                # start the main activity to reset the app
                cmd = 'am start -n \"' + self.package + '/' + self.main_activity + '\"'
                adbutils.adb_shell(cmd, device="emulator-5554")
                pid = self.get_pid(self.package)
                if pid not in pids:
                    pids.append(pid)

            # get names of content providers
            # the information that androguard returns when scanning for content providers is not useful for us
            for provider in self.providers:
                if '[' in provider and ']:' in provider:
                    provider = re.sub('\[|\]|\s|:', '', provider)
                    self.explore_provider(provider)

            # get names of activities
            for activity in self.activities:
                activity = str(activity, "ascii")
                pid = self.explore_activity(activity, self.package, droidmate_conn)
                if pid not in pids:
                    pids.append(pid)

            # stop droidmate
            if self.droidmate:
                droidmate_conn.send_stop()
            else:
                self.message = self.message + "\n\n"

            self.log_success()
        except TimeoutError:
            self.log_success()
            raise TimeoutError(self.message, pids)
        except KeyboardInterrupt:
            self.log_success()
            raise KeyboardInterrupt(self.message, pids)

        return pids, self.droidmate, self.message

    def log_success(self):
        if self.activity_max is not None:
            self.message += "Activities explored: " + str(len(self.activities)) + "/" + str(self.activity_max) + "\n"
        if self.receiver_max is not None:
            self.message += "Receivers explored: " + str(len(self.receivers)) + "/" + str(self.receiver_max) + "\n"
        if self.service_max is not None:
            self.message += "Services explored: " + str(len(self.services)) + "/" + str(self.service_max) + "\n"
        if self.provider_max is not None:
            self.message += "Providers explored: " + str(len(self.providers)) + "/" + str(self.provider_max) + "\n"

    def explore_provider(self, provider):
        # we don't know the exact paths so we simply try to execute a query on the provider itself
        cmd = 'content query --uri content://' + provider + ' --user 0'
        adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)

    def explore_receiver(self, receiver, package, action, category, data, droidmate_conn):
        cmd = 'am broadcast -n \"' + package + '/' + receiver + '\"'
        if action != '':
            cmd = cmd + ' -a ' + action
        if category != '':
            cmd = cmd + ' -c ' + category
        if data != '':
            cmd = cmd + ' -d ' + data
        adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)

        # DroidMate exploration
        if self.droidmate:
            try:
                droidmate_conn.send_go()
            except RuntimeError as err:
                self.message = str(err)
                self.droidmate = False
        else:
            time.sleep(3)

    def explore_service(self, service, package, droidmate_conn):
        cmd = 'am startservice -n \"' + package + '/' + service + '\"'
        adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)
        # DroidMate exploration
        if self.droidmate:
            try:
                droidmate_conn.send_go()
            except RuntimeError as err:
                self.message = str(err)
                self.droidmate = False
        else:
            time.sleep(3)

    def explore_activity(self, activity, package, droidmate_conn):
        cmd = 'am start -n \"' + package + '/' + activity + '\"'
        adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)
        # DroidMate exploration
        pid = self.get_pid(package)
        if self.droidmate:
            try:
                droidmate_conn.send_go()
            except RuntimeError as err:
                self.message = str(err)
                self.droidmate = False
        else:
            time.sleep(3)

        # force stop of app in case of errors
        cmd = 'am force-stop ' + package
        adbutils.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)
        return pid
