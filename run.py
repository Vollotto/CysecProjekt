import os
import sys
import errno
from argparse import ArgumentParser
from pathlib import Path
from time import time

if __name__ == "__main__":
    # main is only used to parse arguments.
    parser = ArgumentParser()

    parser.add_argument("apk", help="path to target apk")
    parser.add_argument("outputpath", help="path to output file")

    # parser.add_argument("-u", "--user", help="activate user mode", action="store_true")
    parser.add_argument("-s", "--strace", help="activate strace logging", action="store_true")
    parser.add_argument("-n", "--network", help="activate network logging", action="store_true")
    parser.add_argument("-c", "--exploration", help="activate component exploration", action="store_true")
    parser.add_argument("-a", "--artist", help="activate internal method tracing with artist", action="store_true")
    parser.add_argument("-e", "--events", help="activate event stimulation", action="store_true")
    parser.add_argument("-f", "--full", help="activates all modules", action="store_true")
    parser.add_argument("-t", "--time", help="time for event stimulation", default=30, type=int)
    parser.add_argument("--snapshot", help="take snapshot at the end of analysis", action="store_true")

    args = parser.parse_args()

    # full means all modules should be enabled
    if args.full:
        args.strace = True
        args.network = True
        args.exploration = True
        args.artist = True
        args.events = True
        args.snapshot = True

    target = Path(args.apk)

    # check for errors in parameters
    if not target.is_file():
        sys.exit("Passed target path needs to be an APK-File")

    output_dir = Path(args.outputpath)

    if not args.outputpath.endswith("/"):
        args.outputpath = args.outputpath + "/"

    if not output_dir.is_dir():
        try:
            os.makedirs(args.outputpath)
        except PermissionError:
            sys.exit("No permission to create output directory")
        except OSError as exception:
            if exception.errno != errno.EEXIST:
                sys.exit("Failed to create output directory")

    # select modules to be run
    modules = []

    if args.strace:
        modules = modules + ["strace"]
    if args.network:
        modules = modules + ["network"]
    if args.artist:
        modules = modules + ["artist"]
    if args.events:
        modules = modules + ["events"]
    if args.exploration:
        modules = modules + ["exploration"]

    start = time()
    try:
        # start analysis
        pass
    finally:
        print("Analysis duration: " + str(time() - start))

