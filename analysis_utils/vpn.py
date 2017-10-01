from analysis_utils import adbutils
import subprocess
import random


class Vpn:

    def __init__(self, path, package):
        self.path = path
        self.package = package
        self.running = False
        self.vpn_server = None
        self.tcpdump = None

    def setup(self):
        base_directory = str(subprocess.check_output("pwd", shell=True), "ascii").strip("\n")
        # setup command to start vpn server
        server_cmd = base_directory + '/vpn/VpnServer tun0' \
                                      ' 40009 test -m 1400 -a 10.0.0.2 32 -d 8.8.8.8 -r 0.0.0.0 0 -z ' + self.package
        # try to start vpn server
        try:
            self.vpn_server = subprocess.Popen(server_cmd, shell=True)
        except subprocess.CalledProcessError as e:
            raise RuntimeError(e.output)

        # tcpdump command
        tcpdump_cmd = 'sudo tcpdump -i tun0 -v -w ' + self.path
        # try to start tcpdump
        try:
            self.tcpdump = subprocess.Popen(tcpdump_cmd, shell=True, stderr=subprocess.PIPE)
        except subprocess.CalledProcessError as e:
            raise RuntimeError(e.output)
        # check if tcpdump started successful
        test_err = str(self.tcpdump.stderr.readline(), "ascii")
        if "listening" not in test_err:
            raise RuntimeError(test_err)

        # start vpn app
        app_success, app_output = adbutils.adb_shell('am start -n com.example.tobki42.vpnsolution/.MainActivity',
                                                     device="emulator-5554")
        # check if vpn app started successful
        if (not app_success) or ("Starting" not in app_output):
            self.vpn_server.kill()
            self.tcpdump.kill()
            raise RuntimeError(app_output)

        self.running = True
        return

    def stop(self, path):
        if self.running:
            self.vpn_server.kill()
            self.tcpdump.kill()
            self.cleanup(path)
        else:
            return

    def cleanup(self, path):
        vpn_count = subprocess.check_output("ls " + path, shell=True).decode().count("vpn")
        cmd = "mv " + self.path + " " + path + "vpn" + str(vpn_count) + ".pcap"
        subprocess.check_output(cmd, shell=True)
