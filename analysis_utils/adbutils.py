from subprocess import check_output, CalledProcessError, STDOUT, Popen, TimeoutExpired
from typing import Union, Tuple
from time import sleep


def adb_devices(string_out: bool = True, device: Union[str, None]=None, timeout: int = 1000) -> Tuple[bool, str]:

    base_directory = str(check_output("echo $ANDROID_HOME", shell=True), "ascii").strip("\n")
    cmd = base_directory \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' devices'
    return shell(cmd, string_out=string_out, timeout=timeout)


def adb_kill_server(string_out: bool = True, device: Union[str, None]=None, timeout: int = 1000) -> Tuple[bool, str]:

    base_directory = str(check_output("echo $ANDROID_HOME", shell=True), "ascii").strip("\n")
    cmd = base_directory \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' kill-server'
    return shell(cmd, string_out=string_out, timeout=timeout)


def adb_install(path: str, string_out: bool = True, reinstall: bool = True, device: Union[str, None] = None, timeout: int = 1000) \
        -> Tuple[bool, str]:
    base_directory = str(check_output("echo $ANDROID_HOME", shell=True), "ascii").strip("\n")
    command = base_directory \
        + '/platform-tools/adb ' \
        + (('-s ' + device + ' ') if device is not None else '') \
        + 'install ' \
        + ('-r ' if reinstall else ' ') \
        + path
    return shell(command, string_out=string_out, timeout=timeout)


def adb_pull(path: str, destination: str, string_out: bool=True, device: Union[str, None]=None, timeout: int=1000) -> Tuple[bool, str]:

    base_directory = str(check_output("echo $ANDROID_HOME", shell=True), "ascii").strip("\n")
    cmd = base_directory \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' pull ' \
        + path + ' ' + destination
    return shell(cmd, string_out=string_out, timeout=timeout)


def adb_push(path: str, destination: str, string_out: bool=True, device: Union[str, None]=None, timeout: int = 1000) -> Tuple[bool, str]:

    base_directory = str(check_output("echo $ANDROID_HOME", shell=True), "ascii").strip("\n")
    cmd = base_directory \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' push ' \
        + path + ' ' + destination
    return shell(cmd, string_out=string_out, timeout=timeout)


def adb_shell(command: str, string_out: bool=True, device: Union[str, None]=None, timeout: int = 1000) -> Tuple[bool, str]:

    base_directory = str(check_output("echo $ANDROID_HOME", shell=True), "ascii").strip("\n")
    cmd = base_directory \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' shell ' \
        + command
    return shell(cmd, string_out=string_out, timeout=timeout)


def adb_popen(command: str, device: Union[str, None]=None) -> Tuple[bool, str]:

    base_directory = str(check_output("echo $ANDROID_HOME", shell=True), "ascii").strip("\n")
    cmd = base_directory \
        + '/platform-tools/adb' \
        + ((' -s ' + device) if device is not None else '') \
        + ' shell ' \
        + command
    try:
        out = Popen(cmd, shell=True)
        success = True
    except CalledProcessError as e:
        if reset_adb():
            return adb_popen(command)
        out = e.output
        success = False

    return success, out


def shell(command: str, string_out: bool=True, timeout: int = 1000) -> Tuple[bool, str]:
    try:
        out = check_output(command.split(" "), stderr=STDOUT, timeout=timeout)
        result = 0
    except CalledProcessError as e:
        if reset_adb():
            return shell(command, string_out)
        out = e.output
        result = e.returncode
    except TimeoutExpired:
        raise TimeoutError

    return result == 0, out if not string_out else out.decode()


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
