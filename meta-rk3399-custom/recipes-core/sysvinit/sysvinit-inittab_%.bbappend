# Modify inittab for RK3399 desktop auto-login
# Replace getty on tty1 with lxdm for automatic desktop login
#
# This modification will only be effective if lxdm is installed in the image.
# For minimal images without lxdm, the line will be present but will fail gracefully.
# Desktop images (rk3399-image-desktop) include lxdm, so auto-login will work correctly.
#
# Note: This bbappend applies to all images using sysvinit-inittab, but only
# desktop images typically include lxdm, making this modification relevant.

do_install:append() {
    if [ -f "${D}${sysconfdir}/inittab" ]; then
        # Replace getty with lxdm for tty1
        if grep -q "^1:12345:respawn:/sbin/getty" "${D}${sysconfdir}/inittab" 2>/dev/null; then
            sed -i 's|^1:12345:respawn:/sbin/getty 38400 tty1|1:12345:respawn:/usr/sbin/lxdm|' "${D}${sysconfdir}/inittab"
            bbnote "Modified inittab: tty1 now starts lxdm for desktop auto-login"
        elif ! grep -q "^1:12345:respawn:/usr/sbin/lxdm" "${D}${sysconfdir}/inittab" 2>/dev/null; then
            # If tty1 line doesn't exist, add it after serial console lines
            # Insert after S2 line if it exists, otherwise at the end
            if grep -q "^S2:12345:respawn:" "${D}${sysconfdir}/inittab"; then
                sed -i '/^S2:12345:respawn:/a 1:12345:respawn:/usr/sbin/lxdm' "${D}${sysconfdir}/inittab"
            else
                echo "1:12345:respawn:/usr/sbin/lxdm" >> "${D}${sysconfdir}/inittab"
            fi
            bbnote "Added inittab entry: tty1 starts lxdm for desktop auto-login"
        fi
    fi
}

