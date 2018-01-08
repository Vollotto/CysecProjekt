class Module(object):

    def __init__(self, host, vm, guest, app):
        self.host = host
        self.vm = vm
        self.guest = guest
        self.app = app


class TracingModule(Module):

    def start_tracing(self):
        raise NotImplementedError("Has to be implemented by subclasses.")

    def stop_tracing(self):
        raise NotImplementedError("Has to be implemented by subclasses")


class ExplorationModule(Module):

    def explore(self):
        raise NotImplementedError("Has to be implemented by subclasses")
