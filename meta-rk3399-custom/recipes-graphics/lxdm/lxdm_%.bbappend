# Configure LXDM for auto-login

inherit update-alternatives

do_install:append() {
    # Configure auto-login for root user
    # LXDM config file location: /etc/lxdm/lxdm.conf
    install -d ${D}${sysconfdir}/lxdm
    
    # Check if config file exists, if not create it
    if [ ! -f ${D}${sysconfdir}/lxdm/lxdm.conf ]; then
        # Create a basic config file if it doesn't exist
        cat > ${D}${sysconfdir}/lxdm/lxdm.conf <<EOF
[base]
last_session=
last_lang=
last_langs=
EOF
    fi
    
    # Remove root from blacklist (if present)
    sed -i 's/^black=.*root.*/# black=/' ${D}${sysconfdir}/lxdm/lxdm.conf
    sed -i 's/black=root/# black=/' ${D}${sysconfdir}/lxdm/lxdm.conf
    sed -i 's/^black=root$/# black=/' ${D}${sysconfdir}/lxdm/lxdm.conf
    
    # Configure autologin - handle multiple config formats
    if grep -q "^[[:space:]]*#[[:space:]]*autologin=" ${D}${sysconfdir}/lxdm/lxdm.conf; then
        # If commented out, uncomment and set to root
        sed -i 's/^[[:space:]]*#[[:space:]]*autologin=.*/autologin=root/' ${D}${sysconfdir}/lxdm/lxdm.conf
    elif grep -q "^[[:space:]]*autologin=" ${D}${sysconfdir}/lxdm/lxdm.conf; then
        # If already set, change it to root
        sed -i 's/^[[:space:]]*autologin=.*/autologin=root/' ${D}${sysconfdir}/lxdm/lxdm.conf
    else
        # If not present, add it to [base] section
        if grep -q "^\[base\]" ${D}${sysconfdir}/lxdm/lxdm.conf; then
            sed -i '/^\[base\]/a autologin=root' ${D}${sysconfdir}/lxdm/lxdm.conf
        else
            echo "" >> ${D}${sysconfdir}/lxdm/lxdm.conf
            echo "[base]" >> ${D}${sysconfdir}/lxdm/lxdm.conf
            echo "autologin=root" >> ${D}${sysconfdir}/lxdm/lxdm.conf
        fi
    fi
    
    # Set timeout to 0 for immediate login
    if grep -q "^[[:space:]]*#[[:space:]]*timeout=" ${D}${sysconfdir}/lxdm/lxdm.conf; then
        sed -i 's/^[[:space:]]*#[[:space:]]*timeout=.*/timeout=0/' ${D}${sysconfdir}/lxdm/lxdm.conf
    elif grep -q "^[[:space:]]*timeout=" ${D}${sysconfdir}/lxdm/lxdm.conf; then
        sed -i 's/^[[:space:]]*timeout=.*/timeout=0/' ${D}${sysconfdir}/lxdm/lxdm.conf
    else
        # Add timeout after autologin
        sed -i '/^autologin=root/a timeout=0' ${D}${sysconfdir}/lxdm/lxdm.conf
    fi
    
    # Set XFCE session as default
    if grep -q "^[[:space:]]*#[[:space:]]*session=" ${D}${sysconfdir}/lxdm/lxdm.conf; then
        sed -i 's|^[[:space:]]*#[[:space:]]*session=.*|session=/usr/bin/startxfce4|' ${D}${sysconfdir}/lxdm/lxdm.conf
    elif grep -q "^[[:space:]]*session=" ${D}${sysconfdir}/lxdm/lxdm.conf; then
        sed -i 's|^[[:space:]]*session=.*|session=/usr/bin/startxfce4|' ${D}${sysconfdir}/lxdm/lxdm.conf
    else
        # Add session after timeout or autologin
        sed -i '/^timeout=0/a session=/usr/bin/startxfce4' ${D}${sysconfdir}/lxdm/lxdm.conf || \
        sed -i '/^autologin=root/a session=/usr/bin/startxfce4' ${D}${sysconfdir}/lxdm/lxdm.conf
    fi
    
    # Fix PAM configuration: Remove pam_systemd.so since we're using sysvinit
    if [ -f "${D}${sysconfdir}/pam.d/lxdm" ]; then
        sed -i '/pam_systemd\.so/d' ${D}${sysconfdir}/pam.d/lxdm
        bbnote "Removed pam_systemd.so from lxdm PAM configuration (sysvinit system)"
    fi
}

# Enable lxdm service to start on boot (default is already "enable", but explicit is better)
SYSTEMD_AUTO_ENABLE:${PN} = "enable"

