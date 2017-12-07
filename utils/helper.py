import psutil


def kill_process_by_name(name):
    for proc in psutil.process_iter():
        if name in proc.name():
            proc.kill()