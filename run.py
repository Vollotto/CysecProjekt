import os
import sys
import errno
from argparse import ArgumentParser
from pathlib import Path
from subprocess import check_output, CalledProcessError
from datetime import datetime
from time import sleep
import psutil

from missioncontrol import MissionControl


class Runner(object):
    
    def __init__(self, time, snapshot):
        self.instrumented = False
        self.control = None
        self.timeout = time
        self.snapshot = snapshot

    def execute_command(self, cmd, stop):
        if cmd == "strace":
            self.control.strace(stop)
        elif cmd == "network":
            self.control.vpn(stop)
        elif cmd == "events":
            self.control.events(False)
            sleep(self.timeout)
            self.control.events(True)
        elif cmd == "exploration":
            # TODO add time to run
            self.control.androguard(True)
        elif cmd == "artist":
            self.control.artist(stop)
        else:
            print("Unknown command.")

    # TODO use vm api instead of subprocess, kniffler
    @staticmethod
    def setup():
        try:
            if "tun0" not in check_output("ifconfig", shell=True).decode():
                print("Creating tunnel interface.")
                check_output("./scripts/setup_tunnel.sh", shell=True)
            if "ANDROID_HOME" not in os.environ:
                print("Error: ANDROID_HOME needs to be set.")
                return False
            print("Restoring snapshot.")
            check_output("VBoxManage snapshot LInux restore 'Droidsand'", shell=True)
            print("Starting emulator.")
            check_output("VBoxManage startvm LInux", shell=True)
            Runner.kill_process_by_name("VpnServer")
        except CalledProcessError as error:
            print(error.output)
            return False
        return True

    @staticmethod
    def kill_process_by_name(name):
        for proc in psutil.process_iter():
            if name in proc.name():
                proc.kill()

    @staticmethod
    def kill_emulator(snapshot=False, name=""):
        Runner.kill_process_by_name("java")
        try:
            if snapshot:
                check_output("VBoxManage snapshot LInux take " + name, shell=True)
            check_output("VBoxManage controlvm LInux poweroff", shell=True)
        except CalledProcessError as error:
            print(error.output)
            Runner.kill_process_by_name("VirtualBox")

    # TODO recreate user mode
    def run(self, start_modules, apk, output):
        # start emulator and environment
        emulator = Runner.setup()
        if not emulator:
            print("Trying to start emulator for the second time.")
            # in case starting failed cause emulator is still running
            Runner.kill_emulator()
            emulator = Runner.setup()
            if not emulator:
                sys.exit("Failed to setup emulator.")
        self.control = MissionControl()
        try:
            if not print(self.control.start(apk, output)):
                sys.exit()
            # start all modules specified by the command line arguments
            for command in start_modules:
                self.execute_command(command, False)
            # stop all modules running
            print(self.control.stop())
            # kill emulator
            Runner.kill_emulator(self.snapshot, apk.split("/")[-1])
        # catch all Exceptions to avoid interfering with later runs
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

    # parser.add_argument("-u", "--user", help="activate user mode", action="store_true")
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
        runner.run(modules, args.apk, args.outputpath)
    except Exception:
        print("Analysis duration: " + str(datetime.now() - start))
        raise
    print("Analysis duration: " + str(datetime.now() - start))
