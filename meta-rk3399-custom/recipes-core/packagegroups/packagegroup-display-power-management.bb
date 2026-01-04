SUMMARY = "Disable display power management and screen blanking"
DESCRIPTION = "This package disables DPMS and screen blanking to prevent VNC connection issues"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit allarch

SRC_URI = ""

do_install() {
    # Create X Server configuration to disable DPMS
    install -d ${D}${sysconfdir}/X11/xorg.conf.d
    cat > ${D}${sysconfdir}/X11/xorg.conf.d/99-disable-dpms.conf << 'EOF'
Section "ServerFlags"
    Option "BlankTime" "0"
    Option "StandbyTime" "0"
    Option "SuspendTime" "0"
    Option "OffTime" "0"
EndSection

Section "Monitor"
    Option "DPMS" "false"
EndSection
EOF

    # Create X session startup script to disable DPMS
    install -d ${D}${sysconfdir}/X11/Xsession.d
    cat > ${D}${sysconfdir}/X11/Xsession.d/99-disable-dpms << 'EOF'
#!/bin/sh
# Disable DPMS and screen blanking
export DISPLAY=:0
xset -dpms
xset s off
xset s noblank
EOF
    chmod +x ${D}${sysconfdir}/X11/Xsession.d/99-disable-dpms

    # Create XFCE power manager default configuration
    install -d ${D}${sysconfdir}/xdg/xfce4/xfconf/xfce-perchannel-xml
    cat > ${D}${sysconfdir}/xdg/xfce4/xfconf/xfce-perchannel-xml/xfce4-power-manager.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfce4-power-manager" version="1.0">
  <property name="xfce4-power-manager" type="empty">
    <property name="dpms-enabled" type="bool" value="false"/>
    <property name="dpms-on-ac-sleep" type="uint" value="0"/>
    <property name="dpms-on-ac-off" type="uint" value="0"/>
    <property name="dpms-on-battery-sleep" type="uint" value="0"/>
    <property name="sleep-on-ac-off" type="int" value="0"/>
    <property name="sleep-on-ac-on" type="int" value="0"/>
    <property name="sleep-on-battery" type="int" value="0"/>
  </property>
</channel>
EOF
}

FILES:${PN} += " \
    ${sysconfdir}/X11/xorg.conf.d/99-disable-dpms.conf \
    ${sysconfdir}/X11/Xsession.d/99-disable-dpms \
    ${sysconfdir}/xdg/xfce4/xfconf/xfce-perchannel-xml/xfce4-power-manager.xml \
"

