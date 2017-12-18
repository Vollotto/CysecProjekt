import os
from subprocess import check_output, CalledProcessError, TimeoutExpired
from utils import helper

BASE_DIRECTORY = os.environ["ANDROID_HOME"]


class VM(object):

    def __init__(self, snapshot):
        self.snapshot = snapshot

    def setup_emulator(self):
        try:
            check_output("VBoxManage snapshot LInux restore 'Droidsand'", shell=True)
            check_output("VBoxManage startvm LInux", shell=True)
        except CalledProcessError:
            self.kill_emulator()
            check_output("VBoxManage snapshot LInux restore 'Droidsand'", shell=True)
            check_output("VBoxManage startvm LInux", shell=True)

    def kill_emulator(self, name=None):
        if self.snapshot:
            check_output("VBoxManage snapshot LInux take " + name, shell=True)
        try:
            check_output("VBoxManage controlvm LInux poweroff", shell=True)
        except CalledProcessError as err:
            print(err.args)
            helper.kill_process_by_name("VirtualBox")


class HostSystem(object):
    def __init__(self):
        self.cleanup()
        self.processes = []
        pass

    # get rid of processes that might interfere with analysis
    @staticmethod
    def cleanup():
        helper.kill_process_by_name("java")
        helper.kill_process_by_name("VpnServer")
        helper.kill_process_by_name("VirtualBox")

    @staticmethod
    def prepare():
        if not os.getenv("ANDROID_HOME"):
            raise RuntimeError
        if "tun0" not in check_output("ifconfig", shell=True):
            check_output("./scripts/setup_tunnel.sh", shell=True)


class GuestSystem(object):

    def __init__(self, name):
        self.name = name
        self.apps_installed = []
        self.processes = {}
        self.running = self.setup()

    def setup(self):
        self.execute_adb_command(" shell kill \"`pgrep fredhunter`\"", device=self.name)
        self.execute_adb_command("shell date `date +%m%d%H%M%Y.%S`", device=self.name)
        # fix system properties for artist
        self.execute_adb_command("shell setprop dalvik.vm.isa.x86.features default", device=self.name)
        self.execute_adb_command("shell setprop dalvik.vm.isa.x86.variant x86")
        # clear logcat
        self.execute_adb_command("logcat -c", device=self.name)
        return True

    def execute_adb_command(self, command: str, device: Union[str, None]=None, timeout: int=300):
        cmd = BASE_DIRECTORY \
            + '/platform-tools/adb' \
            + ((' -s ' + device) if device is not None else '') \
            + ' '
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
            + ' '
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

    def stop_adb_process(self, name: str)
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
                _, new_proc = self.start_adb_process(command, shell=True)
                self.processes[name] = (new_proc, command)
            return True
        else:
            return False
