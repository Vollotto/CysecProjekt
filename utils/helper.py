import psutil
import glob
import os


def kill_process_by_name(name):
    for proc in psutil.process_iter():
        if name in proc.name():
            proc.kill()


def clean_directory(path: str):
    if path.endswith("/"):
        path += "*"
    else:
        path += "/*"
    files = glob.glob(path)
    for f in files:
        os.remove(f)