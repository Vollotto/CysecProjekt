from analysis_utils import adbutils
from analysis_utils.androguard import Androguard


def setup(path):
    adbutils.adb_shell("kill \"`pgrep fredhunter`\"")
    (install_success, install_output) = adbutils.adb_install(path, reinstall=True, device="emulator-5554")
    if "Success" in install_output:
        package = Androguard.get_package(path)
        (ls_success, ls_output) = adbutils.adb_shell("ls /data/app/" + package + "-1/oat/x86")
        if "No such file or directory" not in ls_output:
            x86 = True
        else:
            x86 = False
        adbutils.adb_shell("date `date +%m%d%H%M%Y.%S`", device="emulator-5554")
        adbutils.shell("adb -s emulator-5554 logcat -c")
        return package, x86
    else:
        raise RuntimeError(install_output)
