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
    
    def __init__(self, time, snapshot):
        self.instrumented = False
        self.control = None
        self.running_modules = []
        self.possible_modules = ["strace", "network", "artist", "exploration", "events"]
        self.timeout = time
        self.snapshot = snapshot
 
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
                return
            else:
                self.instrumented = True
        if stop:
            self.running_modules.remove("artist")
        else:
            self.running_modules.append("artist")
        print(artist_success)

    def events(self, _):
        print("Event stimulation started")
        try:
            self.control.events(False)
        except RuntimeError as error:
            print(error.args)
            return
        sleep(self.timeout)
        self.control.events(True)
        print("event stimulatrion ended.")

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
                check_output("./setup_tunnel.sh", shell=True)
            if str(check_output("echo $ANDROID_HOME", shell=True), "ascii") == "":
                print("Error: ANDROID_HOME needs to be set.")
                return False
            print("Restoring snapshot.")
            check_output("VBoxManage snapshot LInux restore 'Droidsand'", shell=True)
            print("Starting emulator.")
            check_output("VBoxManage startvm LInux", shell=True)
            try:
                vpn_pids = str(check_output("pgrep VpnServer", shell=True), "ascii").split("\n")
                if len(vpn_pids) > 0:
                    for pid in vpn_pids[:-1]:
                        cmd = "kill " + str(pid)
                        check_output(cmd, shell=True)
            except CalledProcessError:
                pass
        except CalledProcessError as error:
            print(error.output)
            return False
        return True

    @staticmethod
    def kill_emulator(snapshot, name):
        
        try:
            if snapshot:
                check_output("VBoxManage snapshot LInux take " + name, shell=True)
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
        # start emulator and environment
        emulator = Runner.setup()
        if not emulator:
            print("Trying to start emulator for the second time.")
            # in case starting failed cause emulator is still running
            Runner.kill_emulator(False, "")
            emulator = Runner.setup()
            if not emulator:
                sys.exit("Failed to setup emulator.")

        self.control = MissionControl()
        try:
            try:
                # prepare analysis(emulator)
                print(self.control.start(apk, output))
            except RuntimeError as error:
                sys.exit(error.args)
            # start all modules specified by the command line arguments
            for command in start_modules:
                func = self.function_selector(command)
                func(False)
            # if no manual exploration(= user mode), end analysis
            if not user_mode:
                # stop all modules running
                print(self.control.stop())
                # kill emulator
                Runner.kill_emulator(self.snapshot, apk.split("/")[-1])
            else:
                # start app and select pid
                self.control.generate_pid()
                print("User mode turned on")
                stop = False
                while not stop:
                    # list running and possible modules
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
                    # get new command from user input
                    command = input("Enter module to start(input:<module>) or stop(input:<module> stop):")
                    command = command.split(" ")
                    # check if input is stop module command
                    if len(command) > 1:
                        if command[1] == "stop":
                            # check if input is a valid running module and stop it
                            if command[0] in self.running_modules:
                                func = self.function_selector(command[0])
                                func(True)
                            else:
                                print("Module can't be stopped, not running.")
                        else:
                            print("No valid command.")
                    else:
                        # check if command is valid module and not already running
                        if command[0] in self.possible_modules:
                            if command[0] in self.running_modules:
                                print("Module already running.")
                            else:
                                func = self.function_selector(command[0])
                                func(False)
                        else:
                            # stop command ends analysis
                            if command[0] == "stop":
                                print(self.control.stop())
                                Runner.kill_emulator(self.snapshot, apk.split("/")[-1] )
                                break
                            print("Invalid command.")
        # catch all Exceptions to avoid interferring with later runs
        except KeyboardInterrupt as key:
            print(key.args)
            print(self.control.stop())
            Runner.kill_emulator(self.snapshot, apk.split("/")[-1])
            raise
        except Exception as err:
            print(err.args)
            print(self.control.stop())
            Runner.kill_emulator(self.snapshot, apk.split("/")[-1])
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
    parser.add_argument("--snapshot", help="take snapshot at the end of analysis", action="store_true")

    args = parser.parse_args()

    # full means all modules should be enabled
    if args.full:
        args.strace = True
        args.network = True
        args.exploration = True
        args.artist = True
        args.events = True
        args.snapshot = True

    target = Path(args.apk)

    # check for errors in parameters
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

    # select modules to be run
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
        # start analysis
        runner = Runner(args.time, args.snapshot)
        runner.run(args.user, modules, args.apk, args.outputpath)
    except Exception:
        print("Analysis duration: " + str(datetime.now() - start))
        raise
    print("Analysis duration: " + str(datetime.now() - start))
