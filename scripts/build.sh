#!/bin/bash

# VirtualBox installieren
# Vm importieren
# dependencies(jdk8, java_home, python libraries, gradle)
# build.sh ausführen
cd ..

git submodule update --init

mv androguard_old/androguard androguard

rm -rf androguard_old

mv droidmate.patch modules/droidmate_dir/droidmate.patch

cd modules/droidmate_dir

# TODO at the moment droidmate patch is not sufficient has to be merged with gradlew.patch
git apply droidmate.patch

VBoxManage hostonlyif create;

ifconfig vboxnet0 192.168.56.1 netmask 255.255.255.0;
