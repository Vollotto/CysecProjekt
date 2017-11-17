from analysis_utils import adbutils
from subprocess import check_output, CalledProcessError
from time import sleep
import os
import zipfile


class Artist:

    def __init__(self, path):
        self.path = path
        self.artist_proc = "artist_proc"
        self.running = False

    @staticmethod
    def instrument(app, x86) -> (bool, str):
        # start artistgui instrumentation of target app
        activity = 'saarland.cispa.artist.artistgui' + '/.' + 'MainActivity'
        cmd = 'am start -n ' + activity + ' --es INTENT_EXTRA_PACKAGE ' + app
        (start_success, start_out) = adbutils.adb_shell(cmd, device='emulator-5554')
        # check if artistgui started successful
        if not start_success:
            return False, start_out
        # get path to base.odex
        if x86:
            path = "/data/app/" + app + "-1/oat/x86/base.odex"
        else:
            path = "/data/app/" + app + "-1/oat/x86_64/base.odex"
        # delete old odex file
        adbutils.adb_shell("rm " + path, device="emulator-5554")
        success = False
        # wait till new base.odex is created
        for i in range(0, 240):
            sleep(5)
            ls_out = adbutils.adb_shell("ls -al " + path, device="emulator-5554")
            # artistgui cleanup sets permissions to 777 -> artist success
            if "-rwxrwxrwx" in ls_out:
                success = True
                break
            # artistgui exception handling sets permissions to 770 -> artist failed
            if "-rwxrwx---" in ls_out:
                success = False
                break
        # close artistgui
        cmd = 'am force-stop ' + 'saarland.cispa.artist.artistgui'
        adbutils.adb_shell(cmd, device="emulator-5554")
        if success:
            return True
        else:
            # try to instrument app again
            return Artist.handle_fail(app, path, x86)

    @staticmethod
    def handle_fail(app, path, x86):
        # delete invalid odex file
        adbutils.adb_shell("rm " + path, device="emulator-5554")
        # check if backup of the merged target app has been created
        ls_out = adbutils.adb_shell("ls /sdcard/", device="emulator-5554")[1]
        if app in ls_out:
            # get merged apk and pull it to host
            ls_list = ls_out.split("\r\n")
            app_merged_signed = [x for x in ls_list if app in x][0]
            print(adbutils.adb_pull("/sdcard/" + app_merged_signed,
                                    destination=app_merged_signed, device="emulator-5554", timeout=1200)[1])
            # try to merge dex files with Dexmerger
            if not Artist.prepare_apk(app_merged_signed):
                return False
            # push the newly merged apk back to emulator
            print(adbutils.adb_push(app_merged_signed, destination="/sdcard/"
                                                                   + app + ".apk", device="emulator-5554")[1])
            # run optimization and artist injections again
            try:
                os.remove(app_merged_signed)
            except CalledProcessError:
                pass
            dex2oat_cmd = "'export LD_LIBRARY_PATH=/data/app/saarland.cispa.artist.artistgui-1/" \
                          "lib/x86_64:/data/user/0/saarland.cispa.artist.artistgui/files/artist/lib/;" \
                          + "/data/user/0/saarland.cispa.artist.artistgui/files/artist/dex2oat " \
                          + "--oat-file=" + path \
                          + " --compiler-backend=Optimizing --compiler-filter=everything" \
                            " --generate-debug-info --compile-pic --checksum-rewriting" \
                          + " --dex-file=/sdcard/" + app + ".apk" \
                          + " --dex-location=/data/app/" + app + "-1/base.apk"
            if not x86:
                dex2oat_cmd += " --instruction-set=x86_64" \
                               " --instruction-set-features=smp,ssse3,sse4.1,sse4.2,-avx,-avx2" \
                               " --instruction-set-variant=x86_64 --instruction-set-features=default'"
            else:
                dex2oat_cmd += " --instruction-set=x86" \
                               " --instruction-set-features=smp,ssse3,sse4.1,sse4.2,-avx,-avx2" \
                               " --instruction-set-variant=x86 --instruction-set-features=default'"
            adbutils.adb_shell(dex2oat_cmd, device="emulator-5554")
            
            if "No such file or directory" not in adbutils.adb_shell("ls " + path)[1]:
                return True
            else:
                return False
        else:
            return False

    @staticmethod
    def prepare_apk(apk):
        old_apk = zipfile.ZipFile(apk, "r")
        new_apk = zipfile.ZipFile("temp.apk", "w")
        dex_file = "dexfile.dex"
        for item in old_apk.infolist():
            buffer = old_apk.read(item.filename)
            if ("classes.dex" != item.filename) and (".dex" in item.filename):
                f = open(dex_file, "wb")
                f.write(buffer)
                f.close()
                check_output("java -jar DexTools.jar " + dex_file + " codelib/classes.dex", shell=True)
                new_apk.write(dex_file, item.filename)
                os.remove(dex_file)
            else:
                new_apk.writestr(item, buffer)
        old_apk.close()
        new_apk.close()
        os.remove(apk)
        os.rename(dex_file, apk)
        return True

    @staticmethod
    def grep_log(path):
        log = open(path + "logcat.txt", "r")
        log_without_artist = open(path + "log_without_artist.txt", "w")
        artist_log = open(path + "artist_log.txt", "w")
        for line in log:
            if "ArtistCodeLib" in line:
                artist_log.write(line)
            else:
                log_without_artist.write(line)
