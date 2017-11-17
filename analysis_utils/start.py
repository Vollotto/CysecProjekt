from analysis_utils import adbutils
from analysis_utils.androguard import Androguard


def setup(apk, path):
    adbutils.adb_shell("kill \"`pgrep fredhunter`\"")
    # install app
    (install_success, install_output) = adbutils.adb_install(apk, reinstall=True, device="emulator-5554")
    if "Success" in install_output:
        # get package name with androguard
        package = Androguard.get_package(apk)
        # check if oat file is in x86 or x86_64 directory -> important for strace and artist
        (ls_success, ls_output) = adbutils.adb_shell("ls /data/app/" + package + "-1/oat/x86")
        if "No such file or directory" not in ls_output:
            x86 = True
        else:
            x86 = False
        # sync emulator time with host
        adbutils.adb_shell("date `date +%m%d%H%M%Y.%S`", device="emulator-5554")
        # fix system properties for artist
        adbutils.adb_shell("setprop dalvik.vm.isa.x86.features default", device="emulator-5554")
        adbutils.adb_shell("setprop dalvik.vm.isa.x86.variant x86")
        # clear logcat
        adbutils.shell("adb -s emulator-5554 logcat -c")
        # saving log
        log_cmd = "logcat >> " + path + "logcat.txt"
        log_success = adbutils.adb_popen(log_cmd, "log")
        if not log_success:
            raise RuntimeError("Failed to start logcat.")
        return package, x86
    else:
        raise RuntimeError(install_output)
