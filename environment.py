import os
from time import sleep
from typing import Union
from subprocess import check_output, CalledProcessError, TimeoutExpired, Popen
from utils import helper, error

BASE_DIRECTORY = os.environ["ANDROID_HOME"]


class VM(object):

    def __init__(self, snapshot):
        self.snapshot = snapshot
        self.setup_emulator()

    def setup_emulator(self):
        try:
            check_output("VBoxManage snapshot LInux restore 'Droidsand'", shell=True)
            check_output("VBoxManage startvm LInux", shell=True)
        except CalledProcessError:
            self.kill_emulator()
            try:
                check_output("VBoxManage snapshot LInux restore 'Droidsand'", shell=True)
                check_output("VBoxManage startvm LInux", shell=True)
            except CalledProcessError:
                helper.kill_process_by_name("VirtualBox")
                raise RuntimeError("Failed to start VirtualBox.")

    def kill_emulator(self, name=None):
        if self.snapshot and name:
            check_output("VBoxManage snapshot LInux take " + name, shell=True)
        try:
            check_output("VBoxManage controlvm LInux poweroff", shell=True)
        except CalledProcessError:
            helper.kill_process_by_name("VirtualBox")


class HostSystem(object):

    def __init__(self, setup):
        self.processes = []
        if setup:
            self.setup()

    @staticmethod
    def setup():
        # kill all processes that might interfere with analysis
        helper.kill_process_by_name("java")
        helper.kill_process_by_name("VpnServer")
        helper.kill_process_by_name("VirtualBox")
        # check environment
        if not os.getenv("ANDROID_HOME"):
            raise RuntimeError("environment variable ANDROID_HOME has to be set.")
        if "tun0" not in check_output("ifconfig", shell=True):
            try:
                check_output("./scripts/setup_tunnel.sh", shell=True)
                if "tun0" not in check_output("ifconfig", shell=True):
                    raise error.VpnError("Failed to setup tunnel, network tracing disabled.")
            except CalledProcessError:
                raise error.VpnError("Failed to setup tunnel, network tracing disabled.")


class GuestSystem(object):

    def __init__(self, name):
        self.name = name
        self.apps_installed = []
        self.processes = {}
        self.setup()

    def setup(self):
        if not self.check_status():
            raise RuntimeError("Connecting to emulator with adb failed.")
        self.execute_adb_command(" shell kill \"`pgrep fredhunter`\"", device=self.name)
        self.execute_adb_command("shell date `date +%m%d%H%M%Y.%S`", device=self.name)
        # fix system properties for artist
        self.execute_adb_command("shell setprop dalvik.vm.isa.x86.features default", device=self.name)
        self.execute_adb_command("shell setprop dalvik.vm.isa.x86.variant x86")
        # clear logcat
        self.execute_adb_command("logcat -c", device=self.name)

    def execute_adb_command(self, command: str, device: Union[str, None]=None, timeout: int=300):
        cmd = BASE_DIRECTORY \
            + '/platform-tools/adb' \
            + ((' -s ' + device) if device is not None else '') \
            + ' ' \
            + command
        try:
            out = check_output(cmd, shell=True, timeout=timeout)
            return True, out.decode()
        except CalledProcessError as err:
            if "error: device '(null)' not found" in err.output:
                if self.reset_adb():
                    return self.execute_adb_command(command, device, timeout)
            return False, err.output
        except TimeoutExpired as err:
            if self.check_status():
                return self.execute_adb_command(command, device, timeout)
            raise TimeoutError("Emulator crashed.")  #TODO create custom new error type

    def start_adb_process(self, command: str, name: str, device: Union[str, None]=None, timeout: int=300):
        cmd = BASE_DIRECTORY \
            + '/platform-tools/adb' \
            + ((' -s ' + device) if device is not None else '') \
            + ' ' \
            + command
        try:
            if name not in self.processes:
                proc = Popen(cmd, shell=True, timeout=timeout)
                self.processes[name] = (proc, command)
                return True, proc
            else:
                return False, "Error: Process name already in use."
        except CalledProcessError as err:
            if "error: device '(null)' not found" in err.output:
                if self.reset_adb():
                    return self.start_adb_process(command, name, device, timeout)
            return False, err.output
        except TimeoutExpired as err:
            if self.check_status():
                return self.start_adb_process(command, name, device, timeout)
            raise TimeoutError("Emulator crashed.")  #TODO create custom new error type

    def stop_adb_process(self, name: str):
        if name in self.processes:
            self.processes[name][0].kill()
            del self.processes[name]
            return True
        else:
            return False
            
    # try to establish adb connection to emulator
    def reset_adb(self):
        success, output = self.execute_adb_command(" devices")
        count = 0
        while ("device\n" not in output) and (count < 60):
            sleep(1)
            self.execute_adb_command(" kill-server")
            success, output = self.execute_adb_command(" devices")
        if count != 60:
            for name in self.processes:
                proc, command = self.processes[name]
                proc.kill()
                _, new_proc = self.start_adb_process(command, name)
                self.processes[name] = (new_proc, command)
            return True
        else:
            return False

    def check_status(self):
        print(self.name)
        # TODO implement
        return True
