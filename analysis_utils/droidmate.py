import subprocess
import socket
import os


class Droidmate:

    def __init__(self):
        # socket for communication with the Droidmate process
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        # flag that shows if we are connected to the Droidmate socket
        self.connected = False
        # path = path/to/droidmate/gradlew
        path = os.path.dirname(os.path.abspath(__file__)) + '/gradlew'
        droidmate_cmd = path + " clean :p:com:run"
        print(path)
        # subprocess for droidmate
        self.droidmate = subprocess.Popen(droidmate_cmd, stdout=subprocess.PIPE, shell=True)

    def send_go(self):
        # check if we are not yet connected
        if not self.connected:
            # wait until droidmate is ready
            out = str(self.droidmate.stdout.readline(), encoding='ascii')
            while "Waiting for incoming connection" not in out:
                if "BUILD FAILED" in out or "FAILURE" in out:
                    raise RuntimeError("Droidmate encountered a problem when building,\n"
                                       " for detailed logs consider the logs in "
                                       "VmCeptionHandler/analysis_utils/droidmate/output_device1")
                print(out)
                out = str(self.droidmate.stdout.readline(), encoding='ascii')
            self.socket.settimeout(180.0)
            # establish connection to droidmate socket
            self.socket.connect(("localhost", 42042))
            self.connected = True
            print('Succesfully established connection to Droidmate')
        # send 'go'-signal to droidmate
        try:
            self.socket.sendall(bytes('y', 'ascii'))
        except socket.timeout:
            raise TimeoutError()
        # block until an answer from droidmate has been received
        try:
            answer = self.socket.recv(1)
        except socket.timeout:
            raise TimeoutError()
        print(str(answer))

    def send_stop(self):
        # at this moment we must have an open connection so we can send a stop signal and then clean up
        try:
            self.socket.sendall(bytes('n', 'ascii'))
        except BrokenPipeError:
            pass
        self.socket.close()
        self.droidmate.kill()
