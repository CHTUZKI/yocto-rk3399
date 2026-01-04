SUMMARY = "Armbian-style XFCE desktop environment packagegroup"
DESCRIPTION = "Packagegroup that provides Armbian-style XFCE desktop environment with all essential applications and tools"
LICENSE = "MIT"

PACKAGE_ARCH = "${MACHINE_ARCH}"

inherit packagegroup features_check

REQUIRED_DISTRO_FEATURES = "x11"

# XFCE Desktop Environment Core
RDEPENDS:${PN} += " \
    packagegroup-xfce-base \
    packagegroup-xfce-extended \
    xfce4-screenshooter \
    xfce4-taskmanager \
"

# Note: xfce4-power-manager is in packagegroup-xfce-extended as RRECOMMENDS
# It requires networkmanager, so we add networkmanager as a dependency
# This allows xfce4-power-manager to be installed if desired
RDEPENDS:${PN} += " \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'networkmanager', '', d)} \
"

# Display Manager
# Note: Yocto doesn't have lightdm, use lxdm instead or start XFCE with xinit
# For embedded systems, display manager is optional - can use xinit to start XFCE
RDEPENDS:${PN} += " \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'lxdm', '', d)} \
"

# X Server
# Use packagegroup-core-x11 which includes xserver-xorg and basic X11 utilities
RDEPENDS:${PN} += " \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'packagegroup-core-x11', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xserver-xorg-extension-dri', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xserver-xorg-extension-dri2', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xserver-xorg-extension-glx', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xserver-xorg-extension-extmod', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xserver-xorg-extension-dbe', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xserver-xorg-extension-record', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xserver-xorg-module-libint10', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'xf86-video-fbdev', '', d)} \
"

# Audio System
RDEPENDS:${PN} += " \
    ${@bb.utils.contains('DISTRO_FEATURES', 'pulseaudio', 'pulseaudio', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'pulseaudio', 'pulseaudio-server', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'pulseaudio', 'pulseaudio-misc', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'pulseaudio', 'pavucontrol', '', d)} \
"

# Bluetooth
RDEPENDS:${PN} += " \
    ${@bb.utils.contains('DISTRO_FEATURES', 'bluetooth', 'bluez5', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'bluetooth', 'blueman', '', d)} \
"

# Printing (CUPS)
RDEPENDS:${PN} += " \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'cups', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'x11', 'cups-filters', '', d)} \
"

# File Manager and Utilities
# Note: mousepad, ristretto, catfish, gigolo are in packagegroup-xfce-extended as RRECOMMENDS
# We explicitly add them here to ensure they are installed
RDEPENDS:${PN} += " \
    thunar-volman \
    mousepad \
    ristretto \
    catfish \
    gigolo \
"

# System Tools
RDEPENDS:${PN} += " \
    ${@bb.utils.contains('DISTRO_FEATURES', 'polkit', 'polkit', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'polkit', 'polkit-group-rule-datetime', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'polkit', 'polkit-group-rule-network', '', d)} \
    ${@bb.utils.contains('DISTRO_FEATURES', 'polkit', 'xfce-polkit', '', d)} \
    gvfs \
    dbus-x11 \
    xdg-user-dirs \
    xdg-utils \
"

# Fonts
RDEPENDS:${PN} += " \
    fontconfig \
    fontconfig-utils \
    ttf-dejavu-sans \
    ttf-dejavu-sans-mono \
    ttf-dejavu-serif \
"

# Network Tools
RDEPENDS:${PN} += " \
    cifs-utils \
    ${@bb.utils.contains('DISTRO_FEATURES', 'pam', 'samba-client', '', d)} \
"

# Additional Applications
# Note: xfce4-terminal is already in packagegroup-xfce-base
RDEPENDS:${PN} += " \
    evince \
"

# Display power management
RDEPENDS:${PN} += " \
    packagegroup-display-power-management \
"

# XFCE Panel Plugins
# Note: Correct package names are xfce4-*-plugin, not xfce4-panel-plugin-*
RDEPENDS:${PN} += " \
    xfce4-sensors-plugin \
    xfce4-systemload-plugin \
    ${@bb.utils.contains('DISTRO_FEATURES', 'pulseaudio', 'xfce4-pulseaudio-plugin', '', d)} \
    xfce4-weather-plugin \
    xfce4-xkb-plugin \
    xfce4-whiskermenu-plugin \
"

# Optional: Screensaver
RDEPENDS:${PN} += " \
    xfce4-screensaver \
"

# Optional: System Monitor (use xfce4-taskmanager which is already included)
# Optional: Disk Utility (may not be available in Yocto, skip for now)
# Optional: Package Manager GUI (synaptic may not be available in Yocto, skip for now)

