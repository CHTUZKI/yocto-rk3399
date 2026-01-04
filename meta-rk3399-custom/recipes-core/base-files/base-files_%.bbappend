# Add /dev/shm mount to fstab for X11 shared memory support

do_install:append() {
    # Add /dev/shm to fstab if not already present
    if [ -f "${D}${sysconfdir}/fstab" ]; then
        if ! grep -q "^tmpfs.*/dev/shm" "${D}${sysconfdir}/fstab" 2>/dev/null; then
            echo "tmpfs /dev/shm tmpfs defaults,noexec,nosuid 0 0" >> "${D}${sysconfdir}/fstab"
            bbnote "Added /dev/shm mount to fstab"
        fi
    fi
}

