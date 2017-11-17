from subprocess import check_output, CalledProcessError, STDOUT, Popen, TimeoutExpired
from typing import Union, Tuple
from time import sleep
import os

BASE_DIRECTORY = os.environ["ANDROID_HOME"]
PROCESS_DICT = {}


def adb_devices(string_out: bool = True, device: Union[str, None]=None, timeout: int = 1000) -> Tuple[bool, str]:

    cmd = BASE_DIRECTORY \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' devices'
    return shell(cmd, string_out=string_out, timeout=timeout)


def adb_kill_server(string_out: bool = True, device: Union[str, None]=None, timeout: int = 1000) -> Tuple[bool, str]:

    cmd = BASE_DIRECTORY \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' kill-server'
    return shell(cmd, string_out=string_out, timeout=timeout)


def adb_install(path: str, string_out: bool = True, reinstall: bool = True, device: Union[str, None] = None, timeout: int = 1000) \
        -> Tuple[bool, str]:
    command = BASE_DIRECTORY \
        + '/platform-tools/adb ' \
        + (('-s ' + device + ' ') if device is not None else '') \
        + 'install ' \
        + ('-r ' if reinstall else ' ') \
        + path
    return shell(command, string_out=string_out, timeout=timeout)


def adb_pull(path: str, destination: str, string_out: bool=True, device: Union[str, None]=None, timeout: int=1000) -> Tuple[bool, str]:

    cmd = BASE_DIRECTORY \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' pull ' \
        + path + ' ' + destination
    return shell(cmd, string_out=string_out, timeout=timeout)


def adb_push(path: str, destination: str, string_out: bool=True, device: Union[str, None]=None, timeout: int = 1000) -> Tuple[bool, str]:

    cmd = BASE_DIRECTORY \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' push ' \
        + path + ' ' + destination
    return shell(cmd, string_out=string_out, timeout=timeout)


def adb_shell(command: str, string_out: bool=True, device: Union[str, None]=None, timeout: int = 1000) -> Tuple[bool, str]:

    cmd = BASE_DIRECTORY \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' shell ' \
        + command
    return shell(cmd, string_out=string_out, timeout=timeout)


# start subprocess for the given adb command
def adb_popen(command: str, name: str, device: Union[str, None]=None, reset: bool=False, timeout: int=1000) -> bool:

    cmd = BASE_DIRECTORY \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' ' \
        + command
    try:
        if name not in PROCESS_DICT:
            proc = Popen(cmd, shell=True)
            PROCESS_DICT[name] = (proc, command)
            success = True
        else:
            print("Error: Process name already in use.")
            success = False
    except CalledProcessError as e:
        # if executing command fails restart adb once in case its the cause 
        # else return success=False
        if (not reset) and (reset_adb()):
            return adb_popen(command, name, timeout=timeout, reset=True)
        print(e.output)
        success = False

    return success


def stop_process(name: str) -> bool:
    if name in PROCESS_DICT:
        PROCESS_DICT[name][0].kill()
        del PROCESS_DICT[name]
        return True
    else:
        return False


# execute adb shell command
def shell(command: str, string_out: bool=True, timeout: int=1000, reset: bool=False) -> Tuple[bool, str]:
    try:
        out = check_output(command.split(" "), stderr=STDOUT, timeout=timeout)
        result = 0
    except CalledProcessError as e:
        # if executing command fails restart adb once in case its the cause 
        # else return success=False
        if (not reset) and (reset_adb()):
            return shell(command, timeout=timeout, reset=True)
             
        out = e.output
        result = e.returncode
    except TimeoutExpired:
        raise TimeoutError

    return result == 0, out if not string_out else out.decode()


# try to establish adb connection to emulator
# TODO restart the procs in PROCESS_DICT
def reset_adb():
    (success, output) = adb_devices(device="emulator-5554")
    count = 0
    while ("device\n" not in output) and (count < 60):
        sleep(1)
        adb_kill_server()
        (success, output) = adb_devices(device="emulator-5554")
    if count != 60:
        return True
    else:
        return False
