SUMMARY = "Boot-time real-time tuning for RK3399 (LinuxCNC)"
DESCRIPTION = "Init script that applies CPU governor/performance and scheduler tuning settings for better real-time latency."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit update-rc.d

SRC_URI = " \
    file://rk3399-rt-tune.init \
    file://rk3399-rt-tune.sh \
"

S = "${WORKDIR}"

do_install() {
    install -d ${D}${sbindir}
    install -m 0755 ${WORKDIR}/rk3399-rt-tune.sh ${D}${sbindir}/rk3399-rt-tune.sh

    install -d ${D}${sysconfdir}/init.d
    install -m 0755 ${WORKDIR}/rk3399-rt-tune.init ${D}${sysconfdir}/init.d/rk3399-rt-tune
}

INITSCRIPT_NAME = "rk3399-rt-tune"
INITSCRIPT_PARAMS = "defaults 02"

FILES:${PN} += " \
    ${sbindir}/rk3399-rt-tune.sh \
    ${sysconfdir}/init.d/rk3399-rt-tune \
"
