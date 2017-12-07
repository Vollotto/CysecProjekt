# TODO environment... und module und führt dann irgendwie die Analyse durch denke ich
# TODO der Analyse ablauf ist klar: erst den Host, dann die Vm dann den Gast und dann werden die Module ausgeführt, bis die App fertig analysiert ist.
import app


class Analysis(object):
    def __init__(self, apk_path, modules):
        self.target = app.App(apk_path)
