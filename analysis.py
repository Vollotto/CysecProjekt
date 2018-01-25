# TODO sowohl die prozesse auf dem emulator als auch auf dem host werden indirekt übers environment aufgerufen
# TODO vielleicht kann man mehrere apps laufen lassen
# TODO error handling und shut_down aufrufe müssen angepasst werden...
# TODO add static analysis modules?? is possible but you know it

import sys
import app
from utils.error import VpnError, NotRespondingError
from environment import VM, HostSystem, GuestSystem
from typing import List
from modules import strace, artist, androguard, vpn, event_stimulation
from interruptingcow import timeout
from time import time

class Analysis(object):

    def __init__(self, apk_path: str=None, target: app.App=None, trace_modules: List[str], exploration_modules: List[str], snapshot: bool,
                 output_dir: str, max_time: int):
        if apk_path:
            self.target = app.App(apk_path)
        elif target:
            self.target = target
        else:
            sys.exit("Either path to app or app object has to be given.")
        self.output_dir = output_dir if output_dir.endswith("/") else output_dir + "/"
        self.modules = {}
        self.snapshot = snapshot
        self.time = max_time
        self.trace_modules = trace_modules
        self.exp_modules = exploration_modules
        self.running_modules = []
        self.host, self.vm, self.guest = self.setup_environment()

    def setup_environment(self):
        try:
            host = HostSystem(True)
        except RuntimeError as err:
            sys.exit(err.args[0])
        except VpnError as err:
            print(err.args[0])
            self.trace_modules.remove("network")
            host = HostSystem(False)
        try:
            vm = VM(self.snapshot)
        except RuntimeError as err:
            sys.exit(err.args[0])
        try:
            guest = GuestSystem("emulator-5554")
        except RuntimeError as err:
            vm.kill_emulator()
            sys.exit(err.args[0])
        return host, vm, guest

    def module_selector(self, module: str):
        if module == "strace":
            return strace.Strace(self.host: HostSystem, self.vm: VM, self.guest: GuestSystem, self.target: app.App)
        elif module == "artist":
            return artist.Artist(self.host: HostSystem, self.vm: VM, self.guest: GuestSystem, self.target: app.App)
        elif module == "androguard":
            return androguard.Androguard(self.host: HostSystem, self.vm: VM, self.guest: GuestSystem, self.target: app.App)
        elif module == "events":
            return event_stimulation.EventStimulation(self.host: HostSystem, self.vm: VM, self.guest: GuestSystem, self.target: app.App)
        elif module == "network":
            return vpn.Vpn(self.host: HostSystem, self.vm: VM, self.guest: GuestSystem, self.target: app.App)
        else:
            raise NotImplementedError

    def run(self):
        try:
            with timeout(self.timeout, exception=TimeoutError):
                start = time()
                for m in self.trace_modules:
                    try:
                        m_item = self.module_selector(m)
                        m.start_tracing()
                        self.running_modules.append(m_item)
                    except NotImplementedError:
                        print("Invalid module: " + m)
                finished_modules = []
                try:
                    for m in self.exp_modules:
                        try:
                            m_item = self.module_selector(m)
                            m_item.explore()
                            finished_modules.append(m)
                        except NotImplementedError:
                            print("Invalid module: " + m)
                except NotRespondingError:
                    self.shut_down()
                    for m in finished_modules:
                        self.exploration_modules.remove(m)
                    analysis = Analysis(target=self.target, self.trace_modules, self.exploration_modules, self.snapshot, self.output_dir, self.time - (time() - start))
                    analysis.run()
        except TimeoutError:
            self.shut_down()

    def shut_down():
        # TODO implement this
        pass
