# Fix PAM configuration to remove pam_systemd.so when using sysvinit
# The system uses sysvinit, but DISTRO_FEATURES includes systemd,
# which causes libpam to add pam_systemd.so to common-session.
# Since sysvinit doesn't have pam_systemd.so, we need to remove it.

do_install:append() {
    # Remove pam_systemd.so from common-session if using sysvinit
    # Check if we're actually using sysvinit (not systemd)
    if [ -f "${D}${sysconfdir}/pam.d/common-session" ]; then
        sed -i '/pam_systemd\.so/d' ${D}${sysconfdir}/pam.d/common-session
        bbnote "Removed pam_systemd.so from common-session (sysvinit system)"
    fi
}

