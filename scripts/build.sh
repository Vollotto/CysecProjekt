#!/bin/bash

# VirtualBox installieren
# Vm importieren
# dependencies(jdk8, java_home, python libraries, gradle)
# build.sh ausführen
cd ..

git submodule update --init

mv androguard_old/androguard androguard

rm -rf androguard_old

mv droidmateChanges.patch analysis_utils/droidmate/droidmate.patch

cd analysis_utils/droidmate

git apply droidmate.patch

cd ../..

mv gradlew.patch analysis_utils/droidmate/dev/droidmate/gradlew.patch

cd analysis_utils/droidmate/dev/droidmate/

patch gradlew < gradlew.patch

VBoxManage hostonlyif create;

ifconfig vboxnet0 192.168.56.1 netmask 255.255.255.0;
