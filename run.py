import os
import sys
import errno
from argparse import ArgumentParser
from pathlib import Path
from subprocess import check_output, CalledProcessError
from datetime import datetime
from time import sleep

from missioncontrol import MissionControl


class Runner(object):
    
    def __init__(self, time):
        self.instrumented = False
        self.control = None
        self.running_modules = []
        self.possible_modules = ["strace", "network", "artist", "exploration", "events"]
        self.timeout = time
   
    def strace(self, stop):
        try:
            print(self.control.strace(stop))
        except RuntimeError as error:
            print(error.args)
            return
        if stop:
            self.running_modules.remove("strace")
        else:
            self.running_modules.append("strace")
        
    def exploration(self, _):
        try:
            print(self.control.androguard(True))
        except RuntimeError as error:
            print(error.args)

    def network(self, stop):
        try:
            print(self.control.vpn(stop))
        except RuntimeError as error:
            print(error.args)
            return
        if stop:
            self.running_modules.remove("vpn")
        else:
            self.running_modules.append("vpn")

    def artist(self, stop):
        try:
            artist_success = self.control.artist(stop)
        except RuntimeError as error:
            print(error.args)
            self.instrumented = True
            return
        if not self.instrumented:
            if "instrumentation failed" in artist_success:
                print("Artist instrumentation failed, artist disabled")
                self.possible_modules = ["strace", "network", "exploration", "events"]
            else:
                self.instrumented = True
        if stop:
            self.running_modules.remove("artist")
        else:
            self.running_modules.append("artist")
        print(artist_success)

    def events(self, _):
        try:
            self.control.events(False)
        except RuntimeError as error:
            print(error.args)
            return
        sleep(self.timeout)
        self.control.events(True)

    def function_selector(self, module_string):
        function_selector = {
            'strace': 		self.strace,
            'network': 		self.network,
            'artist': 		self.artist,
            'exploration': 	self.exploration,
            'events':		self.events,
        }
        return function_selector.get(module_string)

    @staticmethod
    def setup():
        try:
            if "tun0" not in check_output("ifconfig", shell=True).decode():
                print("Create tunnel interface.")
                check_output("sudo ./setup_tunnel.sh", shell=True)
            if str(check_output("echo $ANDROID_HOME", shell=True), "ascii") == "":
                print("Error: ANDROID_HOME needs to be set.")
                return False
            print("Restoring snapshot.")
            check_output("VBoxManage snapshot LInux restore 'Droidsand'", shell=True)
            print("Starting emulator.")
            check_output("VBoxManage startvm LInux", shell=True)
        except CalledProcessError as error:
            print(error.output)
            return False
        return True

    @staticmethod
    def kill_emulator():
        try:
            java_pids = str(check_output("pgrep java", shell=True), "ascii").split("\n")
            for pid in java_pids[:-1]:
                try:
                    cmd = "kill " + str(pid)
                    check_output(cmd, shell=True)
                except CalledProcessError:
                    pass
            check_output("VBoxManage controlvm LInux poweroff", shell=True)
        except CalledProcessError as error:
            print(error.output)
            try:
                virtual_box_pids = str(check_output("pgrep VirtualBox", shell=True), "ascii").split("\n")
                if len(virtual_box_pids) > 0:
                    for pid in virtual_box_pids[:-1]:
                        cmd = "kill " + str(pid)
                        check_output(cmd, shell=True)
            except CalledProcessError:
                pass

    def run(self, user_mode, start_modules, apk, output):
        emulator = Runner.setup()
        if not emulator:
            print("Trying to start emulator for the second time.")
            Runner.kill_emulator()
            emulator = Runner.setup()
            if not emulator:
                sys.exit("Failed to setup emulator.")

        self.control = MissionControl()
        try:
            try:
                print(self.control.start(apk, output))
            except RuntimeError as error:
                sys.exit(error.args)

            for command in start_modules:
                func = self.function_selector(command)
                func(False)
            if not user_mode:
                print(self.control.stop())
                Runner.kill_emulator()
            else:
                self.control.generate_pid()
                print("User mode turned on")
                stop = False
                while not stop:
                    print("Currently running:")
                    module_str = ""
                    for run_module in self.running_modules:
                        module_str = module_str + run_module + " "
                    print(module_str)
                    print("Possible modules:")
                    module_str = ""
                    for run_module in self.possible_modules:
                        module_str = module_str + run_module + " "
                    print(module_str)
                    command = input("Enter next command:")
                    command = command.split(" ")
                    if len(command) > 1:
                        if command[1] == "stop":
                            if command[0] in self.running_modules:
                                func = self.function_selector(command[0])
                                func(True)
                            else:
                                print("Module can't be stopped, not running.")
                        else:
                            print("No valid command.")
                    else:
                        if command[0] in self.possible_modules:
                            if command[0] in self.running_modules:
                                print("Module already running.")
                            else:
                                func = self.function_selector(command[0])
                                func(False)
                        else:
                            if command[0] == "stop":
                                print(self.control.stop())
                                Runner.kill_emulator()
                                break
                            print("Invalid command.")
        except KeyboardInterrupt as key:
            print(key.args)
            print(self.control.stop())
            Runner.kill_emulator()
            
        except Exception as err:
            print(err.args)
            print(self.control.stop())
            Runner.kill_emulator()
            raise


if __name__ == "__main__":
    # main is only used to parse arguments.
    parser = ArgumentParser()

    parser.add_argument("apk", help="path to target apk")
    parser.add_argument("outputpath", help="path to output file")

    parser.add_argument("-u", "--user", help="activate user mode", action="store_true")
    parser.add_argument("-s", "--strace", help="activate strace logging", action="store_true")
    parser.add_argument("-n", "--network", help="activate network logging", action="store_true")
    parser.add_argument("-c", "--exploration", help="activate component exploration", action="store_true")
    parser.add_argument("-a", "--artist", help="activate internal method tracing with artist", action="store_true")
    parser.add_argument("-e", "--events", help="activate event stimulation", action="store_true")
    parser.add_argument("-f", "--full", help="activates all modules", action="store_true")
    parser.add_argument("-t", "--time", help="time for event stimulation", default=30, type=int)

    args = parser.parse_args()

    if args.full:
        args.strace = True
        args.network = True
        args.exploration = True
        args.artist = True
        args.events = True

    target = Path(args.apk)

    if not target.is_file():
        sys.exit("Passed target path needs to be an APK-File")

    output_dir = Path(args.outputpath)

    if not args.outputpath.endswith("/"):
        args.outputpath = args.outputpath + "/"

    if not output_dir.is_dir():
        try:
            os.makedirs(args.outputpath)
        except PermissionError:
            sys.exit("Failed to create output directory")
        except OSError as exception:
            if exception.errno != errno.EEXIST:
                sys.exit("Failed to create output directory")

    modules = []

    if args.strace:
        modules = modules + ["strace"]
    if args.network:
        modules = modules + ["network"]
    if args.artist:
        modules = modules + ["artist"]
    if args.events:
        modules = modules + ["events"]
    if args.exploration:
        modules = modules + ["exploration"]


    start = datetime.now()
    try:
        runner = Runner(args.time)
        runner.run(args.user, modules, args.apk, args.outputpath)
    except Exception:
        print("Analysis duration: " + str(datetime.now() - start))
        raise
    print("Analysis duration: " + str(datetime.now() - start))