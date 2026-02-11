#!/bin/sh

# Simple helper to configure a static IPv4 address on the RJ45 Ethernet port.
# Default interface name is eth0; change IFACE below if your kernel uses a
# different name (e.g. enp1s0).

IFACE="${IFACE:-eth0}"
IP_ADDR="192.168.137.5/24"

ip link set "${IFACE}" up 2>/dev/null || true

# Clear existing IPv4 addresses on this interface, then add our static address.
ip addr flush dev "${IFACE}" 2>/dev/null || true
ip addr add "${IP_ADDR}" dev "${IFACE}" 2>/dev/null || true

exit 0

