import re
import time

import app
from modules import droidmate
from utils import adb


class Androguard:

    def __init__(self, output: str, with_droidmate: bool):
        self.output = output
        self.droidmate = with_droidmate
        self.timeout = 180

    @staticmethod
    def generate_pid(app_obj: app.App):
        cmd = 'am start -n \"' + app_obj.package_name + '/' + app_obj.main_activity + '\"'
        adb.adb_shell(cmd, device="emulator-5554")
        return Androguard.get_pid(app_obj.package_name)

    @staticmethod
    def get_pid(package: str):
        cmd = 'ps | grep ' + package
        count = 0
        output = ""
        while output == "":
            output = adb.adb_shell(cmd, device="emulator-5554", timeout=180)[1]
            if output == "":
                count += 1
                if count > 20:
                    raise TimeoutError
                time.sleep(1)
        output = re.sub('\s+', ',', output)
        try:
            return output.split(',')[1]
        except IndexError:
            return None

    def explore(self, app_obj: app.App):
        pids = []
        try:
            # pass target apk to droidmate_dir
            if self.droidmate:
                # prepare connection to droidmate_dir
                droidmate_conn = droidmate.Droidmate(app_obj.path_to_apk)
                try:
                    droidmate_conn.send_go()
                except RuntimeError:
                    self.droidmate = False
                    droidmate_conn = None
            else:
                droidmate_conn = None

            for activity in app_obj.activities:
                pid = self.explore_activity(str(activity, "ascii"), app_obj.package_name, droidmate_conn)
                if pid not in pids:
                    pids.append(pid)

            for service in app_obj.services:
                # start the main activity to reset the app
                pid = self.generate_pid(app_obj)
                if pid not in pids:
                    pids.append(pid)
                self.explore_service(str(service, "ascii"), app_obj.package_name, droidmate_conn)

            for receiver in app_obj.receivers:
                # start the main activity to reset the app
                pid = self.generate_pid(app_obj)
                if pid not in pids:
                    pids.append(pid)
                filters = app_obj.receivers[receiver]
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
                self.explore_receiver(receiver, app_obj.package_name, action, category, data, droidmate_conn)

            # get names of content providers
            # the information that androguard returns when scanning for content providers is not useful for us
            providers = adb.adb_shell('dumpsys package providers | grep '
                                      + app_obj.package_name, device="emulator-5554")[1].split('\n')
            for provider in providers:
                if '[' in provider and ']:' in provider:
                    provider = re.sub('\[|\]|\s|:', '', provider)
                    self.explore_provider(provider)

            # stop droidmate_dir
            if self.droidmate:
                droidmate_conn.send_stop()
        except TimeoutError:
            raise TimeoutError(pids)
        except KeyboardInterrupt:
            raise KeyboardInterrupt(pids)

        return pids, self.droidmate

    def explore_provider(self, provider: str):
        # we don't know the exact paths so we simply try to execute a query on the provider itself
        cmd = 'content query --uri content://' + provider + ' --user 0'
        adb.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)

    def explore_receiver(self, receiver: str, package: str, action: str,
                         category: str, data: str, droidmate_conn: droidmate.Droidmate):
        cmd = 'am broadcast -n \"' + package + '/' + receiver + '\"'
        if action:
            cmd = cmd + ' -a ' + action
        if category:
            cmd = cmd + ' -c ' + category
        if data:
            cmd = cmd + ' -d ' + data
        adb.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)

        # DroidMate exploration
        if self.droidmate:
            try:
                droidmate_conn.send_go()
            except RuntimeError:
                self.droidmate = False
        else:
            time.sleep(3)

    def explore_service(self, service, package, droidmate_conn):
        cmd = 'am startservice -n \"' + package + '/' + service + '\"'
        adb.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)
        # DroidMate exploration
        if self.droidmate:
            try:
                droidmate_conn.send_go()
            except RuntimeError:
                self.droidmate = False
        else:
            time.sleep(3)

    def explore_activity(self, activity, package, droidmate_conn):
        cmd = 'am start -n \"' + package + '/' + activity + '\"'
        adb.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)
        # DroidMate exploration
        pid = self.get_pid(package)
        if self.droidmate:
            try:
                droidmate_conn.send_go()
            except RuntimeError:
                self.droidmate = False
        else:
            time.sleep(3)
        # force stop of app in case of errors
        cmd = 'am force-stop ' + package
        adb.adb_shell(cmd, device="emulator-5554", timeout=self.timeout)
        return pid
