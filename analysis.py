# TODO Also Analysis bekommt die Parameter(Module, Zeit, App), erstellt Host, Vm, Gast und App, diese Sachen bekommen
# TODO dann die einzelnen Module nacheinander. Somit behält die Analysis-Klasse den Überblick und kann gezielt
# TODO Fehler handlen. Die Routine wird so lange betrieben bis entweder die App fertig oder die Zeit fertig ist.
# TODO vielleicht kann man in die Module von ner Oberklasse erben lassen? damit könnte man alle gleich aufrufen und
# TODO gemeinsam abarbeiten. Es müsste vermutlich mindestens zwei Arten geben für Androguard etc und die Tracingmodule.
# TODO vielleicht kann man jetzt auch den Cleanup komplett dem Environment überlassen? Teste mal wie das ist wenn man den adb proc kill_emulator
# TODO ob dann der prozess aufm emulator auch stirbt

import app
from environment import VM, HostSystem, GuestSystem


class Analysis(object):
    def __init__(self, apk_path, modules, output_dir):
        self.target = app.App(apk_path)
        self.output_dir = output_dir if output_dir.endswith("/") else output_dir + "/"
        self.modules = {}
        for module in modules:
            self.modules[module] = False
        self.host, self.vm, self.guest = self.setup()

    def setup(self):

    def run(self):

        pass
