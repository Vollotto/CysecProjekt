from subprocess import check_output, Popen, CalledProcessError
import re
from analysis_utils import adbutils
from time import sleep


class Strace:

    def __init__(self, path, x86):
        self.path = path
        self.x86 = x86
        self.running = False
        self.log_proc = None
    
    @staticmethod 
    def grep_pids(path, pids):
        inp = open(path + "strace.txt", "r")
        outp = open(path + "strace_only_target.txt", "w")
        for line in inp:
            flag = False
            for pid in pids:
                if pid is None:
                    continue
                if pid in line:
                    flag = True
                    break
            if flag:
                outp.write(line)

    def setup(self):
        # get pid of zygote
        if self.x86:
            ps_success, ps_out = adbutils.adb_shell("ps | grep zygote", device="emulator-5554")
        else:
            ps_success, ps_out = adbutils.adb_shell("ps | grep zygote64", device="emulator-5554")
        if not ps_success:
            raise RuntimeError("ps failed.")
        if "zygote" not in ps_out:
            # if zygote not in output -> ps failed
            raise RuntimeError("Failed to get pid:\n" + ps_out)
        # extract pid from ps output
        ps_out_stripped = (ps_out.strip("\n"))
        pid = re.sub("\s+", ",", ps_out_stripped).split(",")[1]
        
        adb_path = str(check_output("echo $ANDROID_HOME", shell=True), "ascii").strip("\n") + "/platform-tools/"
        strace_cmd = adb_path + "adb shell strace -f -p " + pid + " > " + self.path
        self.strace_proc = Popen(strace_cmd, shell=True)
        self.running = True

    def stop(self, path):
        if self.running:
            self.strace_proc.kill()
            try:
                pgrep_success, pgrep_out = adbutils.adb_shell("pgrep strace", device="emulator-5554", timeout=30)
            except TimeoutError:
                pgrep_success = False
            if pgrep_success:
                for strace_pid in pgrep_out.split("\r\n"):
                    if strace_pid != "":
                        adbutils.adb_shell("kill" + strace_pid, device="emulator-5554")
            self.cleanup(path)
        else:
            return

    def cleanup(self, path):
        # open output file of strace instance
        output_tmp = open(self.path, "r")
        # construct path to final result file
        path = path + "/strace.txt"
        # open final result file
        output_final = open(path, "a")
        # add temporary output file to result file
        output_final.write("New strace instance: ---------")
        for line in output_tmp:
            output_final.write(line)
        # remove temporary output file
        rm_cmd = "rm " + self.path
        check_output(rm_cmd, shell=True)
