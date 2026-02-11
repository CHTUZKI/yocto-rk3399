FILESEXTRAPATHS:prepend := "${THISDIR}/ifupdown:"

# Install custom /etc/network/interfaces so that:
# - eth1 (USB RNDIS from phone) uses DHCP with lower metric (higher priority)
# - eth0 (RJ45) uses DHCP with higher metric (fallback when USB not present)
SRC_URI += "file://interfaces"

do_install:append() {
    install -d ${D}/etc/network
    install -m 0644 ${WORKDIR}/interfaces ${D}/etc/network/interfaces
}

