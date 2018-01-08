# TODO Also Analysis bekommt die Parameter(Module, Zeit, App), erstellt Host, Vm, Gast und App, diese Sachen bekommen
# TODO dann die einzelnen Module nacheinander. Somit behält die Analysis-Klasse den Überblick und kann gezielt
# TODO Fehler handlen. Die Routine wird so lange betrieben bis entweder die App fertig oder die Zeit fertig ist.
# TODO vielleicht kann man in die Module von ner Oberklasse erben lassen? damit könnte man alle gleich aufrufen und
# TODO gemeinsam abarbeiten. Es müsste vermutlich mindestens zwei Arten geben für Androguard etc und die Tracingmodule.
# TODO vielleicht kann man jetzt auch den Cleanup komplett dem Environment überlassen? Teste mal wie das ist wenn man den adb proc kill_emulator
# TODO ob dann der prozess aufm emulator auch stirbt.
# TODO sowohl die prozesse auf dem emulator als auch auf dem host werden indirekt übers environment aufgerufen, dann
# TODO kann das nochmal nachräumen in case of errors und man hat die subprocesses alle an einem ort vereint wo sie
# TODO verwaltet werden können
# TODO es sollte auch möglich sein mehrere apps gleichzeitig zu installieren und zu überprüfen..., Problem: VPN ?,
# TODO mehrere packages, easy
import sys
import app
from utils.error import VpnError
from environment import VM, HostSystem, GuestSystem
from typing import List


class Analysis(object):
    def __init__(self, apk_path: str, trace_modules: List[str], exploration_modules: List[str], snapshot: bool,
                 output_dir: str, max_time: int):
        self.target = app.App(apk_path)
        self.output_dir = output_dir if output_dir.endswith("/") else output_dir + "/"
        self.modules = {}
        self.snapshot = snapshot
        self.time = max_time
        self.trace_modules = trace_modules
        self.exp_modules = exploration_modules
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

    # TODO wie machen wir das mit der Zeitbeschränkung? Am Ende sollte es nen kleinen Bericht geben.
    def run(self):

        pass
