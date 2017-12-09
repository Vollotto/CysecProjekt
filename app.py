from androguard.core.bytecodes import apk
import zipfile
import re


class App(object):
    def __init__(self, path_to_apk: str):
        self.path_to_apk = path_to_apk
        self.androlyzed_apk = apk.APK(self.path_to_apk)
        self.package_name = self.androlyzed_apk.get_package()
        self.main_activity = self.androlyzed_apk.get_main_activity()

        self.x86 = self.check_x86_version()

        self.activities = None
        self.services = None
        self.receivers = None

        self.act_count = 0
        self.service_count = 0
        self.receiver_count = 0

    def check_x86_version(self):
        zip_apk = zipfile.ZipFile(self.path_to_apk)
        lib = False
        for name in [member.filename for member in zip_apk.infolist()]:
            if "lib/" in name:
                lib = True
            if "lib/x86_64" in name:
                return False
        return lib

    def set_entrypoints(self):
        self.activities = self.androlyzed_apk.get_activities()
        self.services = self.androlyzed_apk.get_services()
        # get receivers and their intent filters
        receiver_list = self.androlyzed_apk.get_receivers()
        for receiver in receiver_list:
            receiver = str(receiver, "ascii")
            # we need the corresponding intent filters
            intent_filter = self.androlyzed_apk.get_intent_filters("receiver", receiver)
            intent_filter = re.sub('\{|\}|\s|\n|\'|\[|\]', '', str(intent_filter))
            self.receivers[receiver] = intent_filter.split(',')
        self.act_count = len(self.activities)
        self.service_count = len(self.services)
        self.receiver_count = len(self.receivers)
