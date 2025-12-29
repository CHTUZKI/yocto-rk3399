SUMMARY = "Rockchip binary tools and firmware blobs"
DESCRIPTION = "Tools and binary blobs for Rockchip SoCs including DDR init, miniloader, and ATF"
LICENSE = "Proprietary"
# Use rkbin's LICENSE file (will be available after unpack)
# MD5: 11e3673115959bf596feaaa6ea7ce9a5
LIC_FILES_CHKSUM = "file://${S}/LICENSE;md5=11e3673115959bf596feaaa6ea7ce9a5"

inherit deploy
inherit native

# Download from Rockchip or use pre-built
# Note: These blobs are proprietary and must be obtained from Rockchip
SRC_URI = " \
    https://github.com/rockchip-linux/rkbin/archive/refs/heads/master.zip;name=rkbin \
"

# Update checksum - use the actual checksum from the error message
SRC_URI[rkbin.sha256sum] = "8d1396c7cb5c8bf5648af17693c23cd013c0edc43408bcd57d256086fc6ee30a"

S = "${WORKDIR}/rkbin-master"

do_configure[noexec] = "1"

do_compile() {
    # Build tools if needed
    if [ -f "${S}/tools/trust_merger.c" ]; then
        oe_runmake -C ${S}/tools CC="${BUILD_CC}" CFLAGS="${BUILD_CFLAGS}"
    fi
}

do_install() {
    install -d ${D}${datadir}/rkbin
    install -d ${D}${datadir}/rkbin/rk33
    install -d ${D}${bindir}
    
    # Install RK3399 blobs
    # Check both rk33/ and bin/rk33/ directories
    # Use v1.30 files (actual version in rkbin repository)
    if [ -f "${S}/bin/rk33/rk3399_ddr_933MHz_v1.30.bin" ]; then
        install -m 0644 ${S}/bin/rk33/rk3399_ddr_933MHz_v1.30.bin ${D}${datadir}/rkbin/rk33/
        # Create symlink for v1.25 compatibility
        ln -sf rk3399_ddr_933MHz_v1.30.bin ${D}${datadir}/rkbin/rk33/rk3399_ddr_933MHz_v1.25.bin
    elif [ -f "${S}/rk33/rk3399_ddr_933MHz_v1.30.bin" ]; then
        install -m 0644 ${S}/rk33/rk3399_ddr_933MHz_v1.30.bin ${D}${datadir}/rkbin/rk33/
        ln -sf rk3399_ddr_933MHz_v1.30.bin ${D}${datadir}/rkbin/rk33/rk3399_ddr_933MHz_v1.25.bin
    elif [ -f "${S}/bin/rk33/rk3399_ddr_933MHz_v1.25.bin" ]; then
        install -m 0644 ${S}/bin/rk33/rk3399_ddr_933MHz_v1.25.bin ${D}${datadir}/rkbin/rk33/
    elif [ -f "${S}/rk33/rk3399_ddr_933MHz_v1.25.bin" ]; then
        install -m 0644 ${S}/rk33/rk3399_ddr_933MHz_v1.25.bin ${D}${datadir}/rkbin/rk33/
    fi
    if [ -f "${S}/bin/rk33/rk3399_miniloader_v1.30.bin" ]; then
        install -m 0644 ${S}/bin/rk33/rk3399_miniloader_v1.30.bin ${D}${datadir}/rkbin/rk33/
        # Create symlink for v1.26 compatibility
        ln -sf rk3399_miniloader_v1.30.bin ${D}${datadir}/rkbin/rk33/rk3399_miniloader_v1.26.bin
    elif [ -f "${S}/rk33/rk3399_miniloader_v1.30.bin" ]; then
        install -m 0644 ${S}/rk33/rk3399_miniloader_v1.30.bin ${D}${datadir}/rkbin/rk33/
        ln -sf rk3399_miniloader_v1.30.bin ${D}${datadir}/rkbin/rk33/rk3399_miniloader_v1.26.bin
    elif [ -f "${S}/bin/rk33/rk3399_miniloader_v1.26.bin" ]; then
        install -m 0644 ${S}/bin/rk33/rk3399_miniloader_v1.26.bin ${D}${datadir}/rkbin/rk33/
    elif [ -f "${S}/rk33/rk3399_miniloader_v1.26.bin" ]; then
        install -m 0644 ${S}/rk33/rk3399_miniloader_v1.26.bin ${D}${datadir}/rkbin/rk33/
    fi
    # Install usbplug blob (required for RKDevTool Maskrom mode)
    if [ -f "${S}/bin/rk33/rk3399_usbplug_v1.30.bin" ]; then
        install -m 0644 ${S}/bin/rk33/rk3399_usbplug_v1.30.bin ${D}${datadir}/rkbin/rk33/
        # Create symlink for v1.26 compatibility (INI file expects v1.26)
        ln -sf rk3399_usbplug_v1.30.bin ${D}${datadir}/rkbin/rk33/rk3399_usbplug_v1.26.bin
    elif [ -f "${S}/rk33/rk3399_usbplug_v1.30.bin" ]; then
        install -m 0644 ${S}/rk33/rk3399_usbplug_v1.30.bin ${D}${datadir}/rkbin/rk33/
        ln -sf rk3399_usbplug_v1.30.bin ${D}${datadir}/rkbin/rk33/rk3399_usbplug_v1.26.bin
    elif [ -f "${S}/bin/rk33/rk3399_usbplug_v1.26.bin" ]; then
        install -m 0644 ${S}/bin/rk33/rk3399_usbplug_v1.26.bin ${D}${datadir}/rkbin/rk33/
    elif [ -f "${S}/rk33/rk3399_usbplug_v1.26.bin" ]; then
        install -m 0644 ${S}/rk33/rk3399_usbplug_v1.26.bin ${D}${datadir}/rkbin/rk33/
    fi
    # Install BL31 (try v1.36 first, fallback to v1.35)
    # Check both rk33/ and bin/rk33/ directories
    if [ -f "${S}/bin/rk33/rk3399_bl31_v1.36.elf" ]; then
        install -m 0644 ${S}/bin/rk33/rk3399_bl31_v1.36.elf ${D}${datadir}/rkbin/rk33/
        # Also create symlink for v1.35 compatibility
        ln -sf rk3399_bl31_v1.36.elf ${D}${datadir}/rkbin/rk33/rk3399_bl31_v1.35.elf
    elif [ -f "${S}/rk33/rk3399_bl31_v1.36.elf" ]; then
        install -m 0644 ${S}/rk33/rk3399_bl31_v1.36.elf ${D}${datadir}/rkbin/rk33/
        ln -sf rk3399_bl31_v1.36.elf ${D}${datadir}/rkbin/rk33/rk3399_bl31_v1.35.elf
    elif [ -f "${S}/bin/rk33/rk3399_bl31_v1.35.elf" ]; then
        install -m 0644 ${S}/bin/rk33/rk3399_bl31_v1.35.elf ${D}${datadir}/rkbin/rk33/
    elif [ -f "${S}/rk33/rk3399_bl31_v1.35.elf" ]; then
        install -m 0644 ${S}/rk33/rk3399_bl31_v1.35.elf ${D}${datadir}/rkbin/rk33/
    fi
    
    # Install tools
    # Note: trust_merger and loaderimage are provided by rk-binary-native to avoid conflicts
    # Only install mkimage here (rk-binary-native may not provide it)
    if [ -f "${S}/tools/mkimage" ]; then
        install -m 0755 ${S}/tools/mkimage ${D}${bindir}/
    fi
}

FILES:${PN} = "${datadir}/rkbin ${bindir}"

BBCLASSEXTEND = "native nativesdk"

