SUMMARY = "Set static IP 10.10.10.1 on RJ45 Ethernet"
DESCRIPTION = "Simple init script that configures a static IPv4 address 10.10.10.1/24 on the primary Ethernet interface (eth0) at boot."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=349504808fb6a3b4c6aefa7e98c7705c"

inherit update-rc.d

SRC_URI = " \
    file://rk3399-static-ip.init \
    file://rk3399-static-ip.sh \
    file://LICENSE \
"

S = "${WORKDIR}"

do_install() {
    install -d ${D}${sbindir}
    install -m 0755 ${WORKDIR}/rk3399-static-ip.sh ${D}${sbindir}/rk3399-static-ip.sh

    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/rk3399-static-ip.init ${D}${sysconfdir}/init.d/rk3399-static-ip
}

INITSCRIPT_NAME = "rk3399-static-ip"
INITSCRIPT_PARAMS = "defaults 99"

FILES:${PN} += " \
    ${sbindir}/rk3399-static-ip.sh \
    ${sysconfdir}/init.d/rk3399-static-ip \
"

