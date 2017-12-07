import subprocess
import socket
import os

class Droidmate:

	def __init__(self):
		# socket for communication with the Droidmate process
		self.socket = socket.socket(socket.AF_INET,socket.SOCK_STREAM)
		# flag that shows if we are connected to the Droidmate socket
		self.connected = False
		# path = path/to/droidmate_dir/gradlew
		path = os.path.dirname(os.path.abspath(__file__)) + '/gradlew'
		droidmateCmd = path + " :p:com:run"
		# subprocess for droidmate_dir
		self.droidmate = subprocess.Popen(droidmateCmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, shell=True)

	def sendGo(self):
		# check if we are not yet connected
		if not self.connected:
			# wait until droidmate_dir is ready
			out = str(self.droidmate.stdout.readline(),encoding='ascii')
			while "Waiting for incoming connection" not in out:
				if "BUILD FAILED" in out:
					raise RuntimeError("Droidmate encountered a problem when building,\n for detailed logs consider the logs in VmCeptionHandler/modules/droidmate_dir/output_device1")
				print(out)
				out = str(self.droidmate.stdout.readline(),encoding='ascii')
            self.socket.settimeout(180.0)
			# establish connection to droidmate_dir socket
			self.socket.connect(("localhost",42042))
			self.connected = True
			print('Succesfully established connection to Droidmate')
		# send 'go'-signal to droidmate_dir
		self.socket.sendall(bytes('y','ascii'))
		# block until an answer from droidmate_dir has been received
		answer = self.socket.recv(1)
		print(str(answer))

	def sendStop(self):
		# at this moment we must have an open connection so we can send a stop signal and then clean up
		self.socket.sendall(bytes('n', 'ascii'))
		self.socket.close()
		self.droidmate.kill()
