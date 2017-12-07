#!/bin/bash

# Enable IP forwarding
echo 1 > /proc/sys/net/ipv4/ip_forward

# Get default interface 
iface=`route | grep '^default' | grep -o '[^ ]*$'`

# Pick a range of private addresses and perform NAT over eth0.
iptables -t nat -A POSTROUTING -s 10.0.0.0/8 -o ${iface} -j MASQUERADE

# Create a TUN interface.
ip tuntap add dev tun0 mode tun

# Set the addresses and bring up the interface.
ifconfig tun0 10.0.0.1 dstaddr 10.0.0.2 up

