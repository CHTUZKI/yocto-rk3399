# Append to x11vnc recipe to add systemd service or sysvinit script

inherit ${@bb.utils.contains('DISTRO_FEATURES','systemd','systemd','update-rc.d',d)}

FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += "file://x11vnc.service \
            file://x11vnc.init \
           "

SYSTEMD_SERVICE:${PN} = "x11vnc.service"
INITSCRIPT_NAME = "x11vnc"
INITSCRIPT_PARAMS = "defaults 98"

do_install:append() {
    # Install systemd service if systemd is enabled
    if ${@bb.utils.contains('DISTRO_FEATURES','systemd','true','false',d)}; then
        install -d ${D}${systemd_system_unitdir}
        install -m 0644 ${WORKDIR}/x11vnc.service ${D}${systemd_system_unitdir}/x11vnc.service
    fi
    
    # Install sysvinit script if sysvinit is enabled
    if ${@bb.utils.contains('DISTRO_FEATURES','sysvinit','true','false',d)}; then
        install -d ${D}${sysconfdir}/init.d
        install -m 0755 ${WORKDIR}/x11vnc.init ${D}${sysconfdir}/init.d/x11vnc
    fi
}

FILES:${PN} += "${@bb.utils.contains('DISTRO_FEATURES','systemd','${systemd_system_unitdir}/x11vnc.service','',d)}"
FILES:${PN} += "${@bb.utils.contains('DISTRO_FEATURES','sysvinit','${sysconfdir}/init.d/x11vnc','',d)}"

