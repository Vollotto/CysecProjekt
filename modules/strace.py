import os
import re

from utils import adb


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
            for pid in pids:
                if pid is not None and pid in line:
                    outp.write(line)
                    break
        outp.close()
        inp.close()

    def setup(self):
        # get pid of zygote
        if self.x86:
            _, ps_out = adb.adb_shell("ps | grep zygote", device="emulator-5554")
        else:
            _, ps_out = adb.adb_shell("ps | grep zygote64", device="emulator-5554")
        if "zygote" not in ps_out:
            # if zygote not in output -> ps failed
            raise RuntimeError("Failed to get pid:\n" + ps_out)
        # extract pid from ps output
        ps_out_stripped = (ps_out.strip("\n"))
        pid = re.sub("\s+", ",", ps_out_stripped).split(",")[1]
        strace_cmd = "shell strace -f -p " + pid + " >> " + self.path
        self.running = adb.start_adb_process(strace_cmd, self.strace_proc)
        if not self.running:
            raise RuntimeError("Failed to start strace.")

    def stop(self, path):
        if self.running:
            adb.stop_adb_process(self.strace_proc)
            try:
                pgrep_success, pgrep_out = adb.adb_shell("pgrep strace", device="emulator-5554", timeout=30)
                if pgrep_success:
                    for strace_pid in pgrep_out.split("\r\n"):
                        if strace_pid != "":
                            adb.adb_shell("kill" + strace_pid, device="emulator-5554")
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
            if line not in output_final:
                output_final.write(line)
        # remove temporary output file
        os.remove(self.path)
