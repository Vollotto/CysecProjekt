from multiprocessing import Process
from analysis_utils.start import setup
from analysis_utils.vpn import Vpn
from analysis_utils.strace import Strace
from analysis_utils.artist import Artist
from analysis_utils.androguard import Androguard
from analysis_utils.event_stimulation import EventStimulation


class MissionControl:

    def __init__(self):
        self.current_process_list = dict()

        self.pids = []
        self.x86 = True
        self.path_to_apk = ""
        self.path_to_result = ""
        self.package = ""

        self.instrumented = False	    
        self.select_pids = False          
        self.finish = False  	        

    def start(self, apk, output):
        self.path_to_apk = apk		 
        self.path_to_result = output
        # get package name of target app
        self.package, self.x86 = setup(apk)
        return "App successful installed"

    def start_process(self, name):
        # create output path
        path = self.path_to_result + name + "_tmp.txt"
        item = None
        # new instance of module
        if name == "strace":
            item = Strace(path, self.x86)
        if name == "vpn":
            item = Vpn(path, self.package)
        if name == "artist":
            item = Artist(path)
        # setup module tracing
        item.setup()
        return item

    def stop_process(self, name):
        item = self.current_process_list.get(name)
        item.stop(self.path_to_result)
        if not self.finish:
            del self.current_process_list[name]  # remove module from running process_list

    def artist(self, stop):
        artist_out = ""
        if stop:
            self.stop_process("artist")
            return "Method tracing stopped."
        else:
            if not self.instrumented:	    # check if app was instrumented before
                success = Artist.instrument(self.package, self.x86)
                if success:
                    artist_out = "instrumentation successful.\n"
                    self.instrumented = True
                else:
                    return "instrumentation failed."
            artist_item = self.start_process("artist")
            self.current_process_list["artist"] = artist_item
            return artist_out + "Method tracing started."

    def androguard(self, droidmate):
        # create output path
        path = self.path_to_result + "androguard.txt"
        # create new androguard instance
        androguard_item = Androguard(self.path_to_apk, path)
        # start exploration
        try:
            (pids, droidmate, message) = androguard_item.explore(droidmate)
        except KeyboardInterrupt as err:
            self.pids = self.pids + err.args[1]
            raise
        except TimeoutError as err:
            self.pids = self.pids + err.args[1]
            raise
        # add process pids
        self.pids = self.pids + pids
        # check if droidmate was enabled
        if droidmate:
            return "Exploration finished successful:\n" + message
        else:
            return "Exploration finished without Droidmate:\n" + message

    def vpn(self, stop):
        if stop:
            self.stop_process("vpn")
            return "Network tracing stopped."
        else:
            vpn_item = self.start_process("vpn")
            self.current_process_list["vpn"] = vpn_item
            return "Network tracing started."

    def strace(self, stop):
        if stop:
            self.stop_process("strace")
            return "strace stopped."
        else:
            self.select_pids = True
            strace_item = self.start_process("strace")
            # strace started successful
            # add strace to running modules
            self.current_process_list["strace"] = strace_item
            return "strace started."

    def events(self, stop):
        if stop:
            event_item, proc = self.current_process_list.get("events")
            event_item.interrupt()
            proc.terminate()
            proc.join()
            if not self.finish:
                del self.current_process_list["events"]
        else:
            self.generate_pid()
            event_item = EventStimulation(self.package)
            proc = Process(target=event_item.stimulate)
            proc.start()
            self.current_process_list["events"] = (event_item, proc)

    def stop(self):
        self.finish = True
        # finish all currently running processes
        for string in self.current_process_list:
            fun = self.select_function(string)
            print(fun(True))
        if self.select_pids:
            Strace.grep_pids(self.path_to_result, self.pids)
        return "Stopped analysis! Shutting down."

    def generate_pid(self):
        self.pids.append(Androguard.generate_pid(self.path_to_apk, self.package))

    def select_function(self, module_string):
        function_selector = {
            'strace': 		self.strace,
            'vpn': 		    self.vpn,
            'artist': 		self.artist,
            'events':		self.events,
        }
        return function_selector.get(module_string)