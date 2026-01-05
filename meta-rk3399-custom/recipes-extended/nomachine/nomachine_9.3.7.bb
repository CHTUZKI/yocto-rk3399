SUMMARY = "NoMachine Remote Desktop Server"
DESCRIPTION = "NoMachine is a fast remote desktop server that provides secure access to your desktop applications"
HOMEPAGE = "https://www.nomachine.com/"
LICENSE = "Proprietary"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Proprietary;md5=0557f9d92cf58f2ccdd50f62f8ac0b28"

# NoMachine ARM64 (aarch64) package - Version 9.3.7
# Using TAR.GZ format which is simpler than DEB
#
# IMPORTANT: Manual download required due to DNS/network issues
# 
# To download the file manually:
#   wget --no-check-certificate https://web9001.nomachine.com/download/9.3/Arm/nomachine_9.3.7_1_aarch64.tar.gz -O /home/xuning/yocto-rk3399/build/downloads/nomachine_9.3.7_1_aarch64.tar.gz
#
# Or download in browser and place in: ${DL_DIR}/nomachine_9.3.7_1_aarch64.tar.gz
#
# After placing the file, BitBake will automatically use it

# Use file:// protocol - BitBake will look in DL_DIR automatically
# Also include the systemd service file and setup script from files subdirectory
SRC_URI = " \
    file://nomachine_9.3.7_1_aarch64.tar.gz;downloadfilename=nomachine_9.3.7_1_aarch64.tar.gz \
    file://nxserver.service \
    file://nomachine-setup.service \
    file://nomachine-setup.sh \
    file://nomachine-setup.init \
"

# Checksum - will be auto-calculated on first use if empty
# After first successful build, BitBake will show the correct checksum
SRC_URI[sha256sum] = ""

# Extract tar.gz package
S = "${WORKDIR}"

DEPENDS = "xserver-xorg libx11 pseudo-native"

RDEPENDS:${PN} += " \
    xserver-xorg \
    libx11 \
    bash \
    grep \
    sed \
    coreutils \
"

inherit systemd

SYSTEMD_SERVICE:${PN} = "nxserver.service"
SYSTEMD_AUTO_ENABLE:${PN} = "enable"
# nomachine-setup.service is installed but optional, not managed by systemd class

# NoMachine installs to /usr/NX
FILES:${PN} += " \
    /usr/NX \
    /usr/NX/* \
    ${bindir}/nxserver \
    ${bindir}/nxnode \
    ${systemd_system_unitdir}/nxserver.service \
    ${systemd_system_unitdir}/nomachine-setup.service \
    ${bindir}/nomachine-setup.sh \
    ${sysconfdir}/init.d/nomachine-setup \
    ${sysconfdir}/nxserver \
    /var/NX \
"

python do_unpack() {
    import os
    import shutil
    
    # First call base unpack to handle all file:// files (including .service, .sh, .init files)
    # This will copy all file:// files from SRC_URI to WORKDIR
    bb.build.exec_func('base_do_unpack', d)
    
    # Now extract the tar.gz file manually
    workdir = d.getVar('WORKDIR')
    dl_dir = d.getVar('DL_DIR')
    
    # Look for the tar.gz file in WORKDIR (BitBake copies it here from DL_DIR)
    tar_file = os.path.join(workdir, "nomachine_9.3.7_1_aarch64.tar.gz")
    
    # If not in WORKDIR, check DL_DIR
    if not os.path.exists(tar_file):
        tar_file = os.path.join(dl_dir, "nomachine_9.3.7_1_aarch64.tar.gz")
    
    nx_dir = os.path.join(workdir, "NX")
    
    # Extract if tar.gz exists and NX directory doesn't exist
    if os.path.exists(tar_file) and not os.path.exists(nx_dir):
        bb.note("Extracting NoMachine tar.gz: %s" % tar_file)
        import subprocess
        result = subprocess.run(['tar', '-xzf', tar_file, '-C', workdir], 
                              capture_output=True, text=True)
        if result.returncode != 0:
            bb.fatal("Failed to extract NoMachine tar.gz: %s" % result.stderr)
    elif not os.path.exists(tar_file):
        bb.warn("NoMachine tar.gz not found. Expected at: %s or %s" % 
                (os.path.join(workdir, "nomachine_9.3.7_1_aarch64.tar.gz"), tar_file))
    
    # Verify extraction
    if not os.path.exists(nx_dir):
        bb.fatal("NX directory not found after extraction. Check tar.gz file structure.")
    
    # Verify that file:// files are in WORKDIR
    bb.note("Verifying file:// files are in WORKDIR...")
    for file in ["nxserver.service", "nomachine-setup.service", "nomachine-setup.sh", "nomachine-setup.init"]:
        file_path = os.path.join(workdir, file)
        if os.path.exists(file_path):
            bb.note("Found %s in WORKDIR" % file)
        else:
            bb.warn("%s not found in WORKDIR" % file)
}

do_install() {
    # Install NoMachine files
    # TAR.GZ package extracts to NX/ directory, we need to copy it to /usr/NX
    NX_SOURCE=""
    
    # Check various possible locations
    if [ -d "${WORKDIR}/NX" ]; then
        NX_SOURCE="${WORKDIR}/NX"
        bbnote "Found NX directory at ${WORKDIR}/NX"
    elif [ -d "${S}/NX" ]; then
        NX_SOURCE="${S}/NX"
        bbnote "Found NX directory at ${S}/NX"
    else
        # Try to find NX directory anywhere in WORKDIR
        NX_DIR=$(find ${WORKDIR} -type d -name "NX" 2>/dev/null | head -1)
        if [ -n "$NX_DIR" ]; then
            NX_SOURCE="$NX_DIR"
            bbnote "Found NX directory at: $NX_SOURCE"
        fi
    fi
    
    if [ -z "$NX_SOURCE" ] || [ ! -d "$NX_SOURCE" ]; then
        bbfatal "Could not find NoMachine NX directory. Searched in: ${WORKDIR}/NX, ${S}/NX, and ${WORKDIR}"
    fi
    
    # Copy NX directory to /usr/NX
    install -d ${D}/usr
    # Use tar to preserve structure and reset ownership
    cd "$NX_SOURCE/.."
    tar -cf - NX | (cd ${D}/usr && tar -xf -) || bbfatal "Failed to copy NX directory to ${D}/usr/NX"
    
    bbnote "NoMachine files installed to ${D}/usr/NX"
    
    # Fix ownership and permissions - critical for avoiding host contamination
    # Reset all ownership to root:root
    chown -R root:root ${D}/usr/NX || true
    # Fix permissions for executables
    if [ -d ${D}/usr/NX ]; then
        find ${D}/usr/NX -type f -name "nxserver" -exec chmod 755 {} \; 2>/dev/null || true
        if [ -d ${D}/usr/NX/bin ]; then
            find ${D}/usr/NX/bin -type f -exec chmod 755 {} \; 2>/dev/null || true
        fi
        if [ -d ${D}/usr/NX/lib ]; then
            find ${D}/usr/NX/lib -type f -name "*.so*" -exec chmod 755 {} \; 2>/dev/null || true
        fi
        # Ensure directories have correct permissions
        find ${D}/usr/NX -type d -exec chmod 755 {} \; 2>/dev/null || true
    fi
    
    # Install systemd service file
    # File is copied to WORKDIR by BitBake from files/ subdirectory
    install -d ${D}${systemd_system_unitdir}
    if [ -f ${WORKDIR}/nxserver.service ]; then
        install -m 0644 ${WORKDIR}/nxserver.service ${D}${systemd_system_unitdir}/nxserver.service
    else
        # Create a basic service file if recipe file doesn't exist
        bbwarn "nxserver.service not found in WORKDIR, creating basic service file"
        cat > ${D}${systemd_system_unitdir}/nxserver.service << 'SERVICE_EOF'
[Unit]
Description=NoMachine Remote Desktop Server
After=network.target syslog.target

[Service]
Type=forking
ExecStart=/usr/NX/nxserver --startup
ExecStop=/usr/NX/nxserver --shutdown
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
SERVICE_EOF
        chmod 0644 ${D}${systemd_system_unitdir}/nxserver.service
    fi
    
    # Install nomachine-setup.service (first boot configuration)
    # Always install it if file exists in SRC_URI
    if [ -f ${WORKDIR}/nomachine-setup.service ]; then
        install -m 0644 ${WORKDIR}/nomachine-setup.service ${D}${systemd_system_unitdir}/nomachine-setup.service
        bbnote "Installed nomachine-setup.service"
    else
        bbwarn "nomachine-setup.service not found in WORKDIR, skipping"
    fi
    
    # Install nomachine-setup.sh script
    if [ -f ${WORKDIR}/nomachine-setup.sh ]; then
        install -d ${D}${bindir}
        install -m 0755 ${WORKDIR}/nomachine-setup.sh ${D}${bindir}/nomachine-setup.sh
    fi
    
    # Install nomachine-setup.init script for SysV init systems
    if [ -f ${WORKDIR}/nomachine-setup.init ]; then
        install -d ${D}${sysconfdir}/init.d
        install -m 0755 ${WORKDIR}/nomachine-setup.init ${D}${sysconfdir}/init.d/nomachine-setup
    fi
    
    # Create nxserver config directory
    install -d ${D}${sysconfdir}/nxserver
    
    # Create /var/NX directory for runtime files
    install -d ${D}/var/NX
    
    # Note: /run/systemd is a runtime directory (tmpfs) and should not be
    # installed in the image. It will be created by the init system or
    # by the post-install script if needed.
}

do_install:append() {
    # Create symlink for nxserver command (if not exists)
    install -d ${D}${bindir}
    if [ -f ${D}/usr/NX/bin/nxserver ]; then
        ln -sf /usr/NX/bin/nxserver ${D}${bindir}/nxserver || true
    elif [ -f ${D}/usr/NX/nxserver ]; then
        ln -sf /usr/NX/nxserver ${D}${bindir}/nxserver || true
    fi
    
    # Create symlink for nxnode command (if not exists)
    if [ -f ${D}/usr/NX/bin/nxnode ]; then
        ln -sf /usr/NX/bin/nxnode ${D}${bindir}/nxnode || true
    fi
}

# Post-install script - setup service will handle first boot configuration
# The nomachine-setup.service (systemd) or nomachine-setup.init (SysV) will run on first boot
pkg_postinst:${PN}() {
    if [ -z "$D" ]; then
        # Reload systemd if available
        if command -v systemctl >/dev/null 2>&1; then
            systemctl daemon-reload
            systemctl enable nxserver.service || true
            # Enable nomachine-setup.service if it exists
            if [ -f /lib/systemd/system/nomachine-setup.service ] || [ -f /usr/lib/systemd/system/nomachine-setup.service ]; then
                systemctl enable nomachine-setup.service || true
            fi
        else
            # For SysV init, enable the setup script to run on first boot
            if [ -f /etc/init.d/nomachine-setup ]; then
                update-rc.d nomachine-setup defaults 99 2 3 4 5 . || true
            fi
            if [ -f /etc/init.d/nxserver ]; then
                update-rc.d nxserver defaults || true
            fi
        fi
    fi
}

pkg_postrm:${PN}() {
    if [ -z "$D" ]; then
        if command -v systemctl >/dev/null 2>&1; then
            systemctl stop nxserver.service || true
            systemctl disable nxserver.service || true
        else
            if [ -f /etc/init.d/nxserver ]; then
                /etc/init.d/nxserver stop || true
                update-rc.d -f nxserver remove || true
            fi
        fi
    fi
}
