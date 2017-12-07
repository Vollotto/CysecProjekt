import os

from subprocess import check_output, CalledProcessError

from utils import helper, adb


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
        self.running = self.setup()

    def setup(self):
        adb.adb_shell("kill \"`pgrep fredhunter`\"", device=self.name)
        adb.adb_shell("date `date +%m%d%H%M%Y.%S`", device=self.name)
        # fix system properties for artist
        adb.adb_shell("setprop dalvik.vm.isa.x86.features default", device=self.name)
        adb.adb_shell("setprop dalvik.vm.isa.x86.variant x86")
        # clear logcat
        adb.adb_shell("logcat -c", device=self.name)
        return True

