#!/bin/bash

VBoxManage hostonlyif create; 
ifconfig vboxnet0 192.168.56.1 netmask 255.255.255.0;
