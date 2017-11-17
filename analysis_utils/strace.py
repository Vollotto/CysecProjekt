from subprocess import Popen
import re
import os
from analysis_utils import adbutils


class Strace:

    def __init__(self, path, x86):
        self.path = path
        self.x86 = x86
        self.running = False
        self.strace_proc = "strace_proc"
    
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
        strace_cmd = "shell strace -f -p " + pid + " >> " + self.path
        self.running = adbutils.adb_popen(strace_cmd, self.strace_proc)
        if not self.running:
            raise RuntimeError("Failed to start strace.")

    def stop(self, path):
        if self.running:
            adbutils.stop_process(self.strace_proc)
            try:
                pgrep_success, pgrep_out = adbutils.adb_shell("pgrep strace", device="emulator-5554", timeout=30)
                if pgrep_success:
                    for strace_pid in pgrep_out.split("\r\n"):
                        if strace_pid != "":
                            adbutils.adb_shell("kill" + strace_pid, device="emulator-5554")
            except TimeoutError:
                pass
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
        os.remove(self.path)
