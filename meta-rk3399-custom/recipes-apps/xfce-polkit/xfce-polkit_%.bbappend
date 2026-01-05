# Fix xfce-polkit for embedded systems running as root
# Disable autostart to prevent multiple instances and error dialogs

do_install:append() {
    # Disable autostart of xfce-polkit to prevent error dialogs
    # In embedded systems running as root, polkit authentication is not necessary
    # Create a system-wide override to disable autostart
    install -d ${D}${sysconfdir}/xdg/autostart
    cat > ${D}${sysconfdir}/xdg/autostart/xfce-polkit.desktop << 'EOF'
[Desktop Entry]
Type=Application
Name=XFCE PolKit
Comment=Policykit Authentication Agent
Exec=/usr/libexec/xfce-polkit
Icon=gtk-dialog-authentication
NotShowIn=GNOME;KDE;
Hidden=true
X-GNOME-Autostart-enabled=false
EOF
    bbnote "Disabled xfce-polkit autostart to prevent error dialogs in embedded system"
}

