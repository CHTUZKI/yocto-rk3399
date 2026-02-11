SUMMARY = "Disable display power management and screen blanking"
DESCRIPTION = "This package disables DPMS and screen blanking to prevent screen going blank during inactivity"

LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit allarch

SRC_URI = ""

do_install() {
    # Create X Server configuration to disable DPMS and fix errors
    install -d ${D}${sysconfdir}/X11/xorg.conf.d
    cat > ${D}${sysconfdir}/X11/xorg.conf.d/99-disable-dpms.conf << 'EOF'
Section "ServerFlags"
    Option "BlankTime" "0"
    Option "StandbyTime" "0"
    Option "SuspendTime" "0"
    Option "OffTime" "0"
    Option "DontVTSwitch" "false"
    Option "DontZap" "false"
    Option "DontZoom" "false"
    Option "AutoEnableDevices" "true"
    Option "AutoAddDevices" "true"
EndSection

Section "Monitor"
    Identifier "DefaultMonitor"
    Option "DPMS" "false"
EndSection

Section "Device"
    Identifier "DefaultDevice"
    Driver "fbdev"
    Option "fbdev" "/dev/fb0"
    Option "ShadowFB" "false"
EndSection

Section "Files"
    FontPath "/usr/share/fonts/TTF/"
    FontPath "/usr/share/fonts/Type1/"
    FontPath "/usr/share/fonts/100dpi/"
    FontPath "/usr/share/fonts/75dpi/"
    FontPath "/usr/share/fonts/misc/"
EndSection
EOF

    # Provide XFCE desktop default: uniform solid background (no gradient)
    # color-style 1 = solid; set color1 and color2 same to avoid half-deep/half-light
    # Ensure target directory exists before writing xfce4-desktop.xml
    install -d ${D}${sysconfdir}/xdg/xfce4/xfconf/xfce-perchannel-xml
    cat > ${D}${sysconfdir}/xdg/xfce4/xfconf/xfce-perchannel-xml/xfce4-desktop.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfce4-desktop" version="1.0">
  <property name="backdrop" type="empty">
    <property name="screen0" type="empty">
      <property name="monitor0" type="empty">
        <property name="workspace0" type="empty">
          <property name="color-style" type="int" value="1"/>
          <property name="color1" type="string" value="#003366"/>
          <property name="color2" type="string" value="#003366"/>
          <property name="image-style" type="int" value="0"/>
        </property>
        <property name="workspace1" type="empty">
          <property name="color-style" type="int" value="1"/>
          <property name="color1" type="string" value="#003366"/>
          <property name="color2" type="string" value="#003366"/>
          <property name="image-style" type="int" value="0"/>
        </property>
        <property name="workspace2" type="empty">
          <property name="color-style" type="int" value="1"/>
          <property name="color1" type="string" value="#003366"/>
          <property name="color2" type="string" value="#003366"/>
          <property name="image-style" type="int" value="0"/>
        </property>
        <property name="workspace3" type="empty">
          <property name="color-style" type="int" value="1"/>
          <property name="color1" type="string" value="#003366"/>
          <property name="color2" type="string" value="#003366"/>
          <property name="image-style" type="int" value="0"/>
        </property>
      </property>
      <property name="monitor1" type="empty">
        <property name="workspace0" type="empty">
          <property name="color-style" type="int" value="1"/>
          <property name="color1" type="string" value="#003366"/>
          <property name="color2" type="string" value="#003366"/>
          <property name="image-style" type="int" value="0"/>
        </property>
        <property name="workspace1" type="empty">
          <property name="color-style" type="int" value="1"/>
          <property name="color1" type="string" value="#003366"/>
          <property name="color2" type="string" value="#003366"/>
          <property name="image-style" type="int" value="0"/>
        </property>
        <property name="workspace2" type="empty">
          <property name="color-style" type="int" value="1"/>
          <property name="color1" type="string" value="#003366"/>
          <property name="color2" type="string" value="#003366"/>
          <property name="image-style" type="int" value="0"/>
        </property>
        <property name="workspace3" type="empty">
          <property name="color-style" type="int" value="1"/>
          <property name="color1" type="string" value="#003366"/>
          <property name="color2" type="string" value="#003366"/>
          <property name="image-style" type="int" value="0"/>
        </property>
      </property>
    </property>
  </property>
</channel>
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

    # Prevent xfce4-screensaver from starting automatically
    install -d ${D}${sysconfdir}/xdg/autostart
    cat > ${D}${sysconfdir}/xdg/autostart/xfce4-screensaver.desktop << 'EOF'
[Desktop Entry]
Type=Application
Name=XFCE Screensaver
Exec=xfce4-screensaver
OnlyShowIn=XFCE;
Hidden=true
EOF

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

    # Create X session script to fix notification area selection issue
    # This script ensures systray plugin properly acquires X11 selection
    # The error occurs when multiple instances compete for X11 selection or panel restarts
    install -d ${D}${sysconfdir}/X11/Xsession.d
    cat > ${D}${sysconfdir}/X11/Xsession.d/98-fix-notification-area << 'EOF'
#!/bin/sh
# Fix XFCE notification area "lost selection" error
# This error occurs when systray plugin loses X11 selection ownership
# Solution: Restart the panel after a short delay to reacquire selection
if [ -n "$DISPLAY" ]; then
    # Wait for XFCE session to fully initialize
    sleep 3
    
    # Restart xfce4-panel to fix systray selection issue
    # This ensures systray plugin properly acquires X11 selection
    if command -v xfce4-panel >/dev/null 2>&1; then
        xfce4-panel --restart 2>/dev/null || true
    fi
fi
EOF
    chmod +x ${D}${sysconfdir}/X11/Xsession.d/98-fix-notification-area
}

FILES:${PN} += " \
    ${sysconfdir}/X11/xorg.conf.d/99-disable-dpms.conf \
    ${sysconfdir}/X11/Xsession.d/99-disable-dpms \
    ${sysconfdir}/X11/Xsession.d/98-fix-notification-area \
    ${sysconfdir}/xdg/xfce4/xfconf/xfce-perchannel-xml/xfce4-power-manager.xml \
    ${sysconfdir}/xdg/xfce4/xfconf/xfce-perchannel-xml/xfce4-desktop.xml \
"

