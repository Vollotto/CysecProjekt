# TODO Also Analysis bekommt die Parameter(Module, Zeit, App), erstellt Host, Vm, Gast und App, diese Sachen bekommen
# TODO dann die einzelnen Module nacheinander. Somit behält die Analysis-Klasse den Überblick und kann gezielt
# TODO Fehler handlen. Die Routine wird so lange betrieben bis entweder die App fertig oder die Zeit fertig ist.
import app


class Analysis(object):
    def __init__(self, apk_path, modules):
        self.target = app.App(apk_path)
