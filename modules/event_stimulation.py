import datetime
import os
import threading
import time
from random import randint

from utils import adb


class EventStimulation:

    def __init__(self, package):
        self.system_events = []
        self.term = False
        self.lock = threading.Lock()
        self.package = package
        path = os.getcwd() + '/docs/broadcast_actions.txt'
        with open(path) as events:
            self.system_events = events.read().splitlines()

    def stimulate(self):
        count = 0
        # iterate over list of all standard broadcast_actions
        while (not self.check_interrupted()) and (count < len(self.system_events)):
            action = self.system_events[count]
            count += 1
            EventStimulation.log('Sending system event -- action: ' + action)
            # send broadcast to target apk
            EventStimulation.log(adb.adb_shell('am broadcast -a ' + action + ' -p ' + self.package)[1])
            wait = randint(5, 12)
            EventStimulation.log('Waiting for ' + str(wait) + ' seconds')
            time.sleep(wait)

    def check_interrupted(self):
        self.lock.acquire()
        try:
            tmp = self.term
        finally:
            self.lock.release()
        return tmp

    @staticmethod
    def log(log_string):
        print('[' + str(datetime.datetime.now()) + '] ' + log_string + "\n")

    def interrupt(self):
        self.lock.acquire()
        try:
            self.term = True
        finally:
            self.lock.release()
