SUMMARY = "Sync system time from NTP at boot"
DESCRIPTION = "Initscript that runs ntpdate at boot so system time is correct for apt/gpg and avoids 'Release file is not valid yet'."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

RDEPENDS:${PN} = "ntpdate"

SRC_URI = "file://rk3399-time-sync.init"

S = "${WORKDIR}"

inherit update-rc.d

do_install() {
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/rk3399-time-sync.init ${D}${sysconfdir}/init.d/rk3399-time-sync
}

INITSCRIPT_NAME = "rk3399-time-sync"
INITSCRIPT_PARAMS = "defaults 99"

FILES:${PN} = "${sysconfdir}/init.d/rk3399-time-sync"
