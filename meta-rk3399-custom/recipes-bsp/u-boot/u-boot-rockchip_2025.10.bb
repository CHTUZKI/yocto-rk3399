SUMMARY = "U-Boot bootloader for Rockchip RK3399"
DESCRIPTION = "U-Boot bootloader with Rockchip-specific patches and configuration"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://Licenses/gpl-2.0.txt;md5=b234ee4d69f5fce4486a80fdaf4a4263"

require recipes-bsp/u-boot/u-boot.inc
require u-boot-rockchip.inc

PROVIDES += "u-boot"

SRC_URI = " \
    git://source.denx.de/u-boot/u-boot.git;protocol=https;branch=master \
    file://rk3399-firefly.dts \
"
# Use fixed commit hash for v2024.10 tag (stable release)
SRCREV = "573d69af36cfb10d58e189656b03a659654b0cd9"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

# Rockchip specific patches
# Note: Some patches may not apply to U-Boot 2024.10 due to code structure changes
# Temporarily disabled patches that fail to apply - can be re-enabled after updating
# SRC_URI += " \
#     file://0001-rk3399-enable-stable-mac.patch \
#     file://0002-rk3399-always-init-rkclk.patch \
#     file://0003-rk3399-ehci-probe-usb2.patch \
#     file://0004-rk3399-populate-child-node-of-syscon.patch \
# "

# Firefly RK3399 defconfig
UBOOT_MACHINE = "firefly-rk3399_defconfig"

# Build configuration
UBOOT_LOCALVERSION = "-rk3399"

# Output files for only-blobs scenario
# U-Boot will build u-boot-dtb.bin, which will be packaged into uboot.img by rk3399-blobs
UBOOT_BINARY = "u-boot-dtb.bin"
UBOOT_MAKE_TARGET = "all"

# Rockchip boot scenario: only-blobs
# Uses proprietary blobs: DDR init, miniloader, and ATF (BL31)
# Final images: idbloader.bin, uboot.img, trust.bin
RK3399_BOOT_SCENARIO = "only-blobs"

# Note: BL31 is not used directly in U-Boot build for only-blobs scenario
# It's used by rk3399-blobs recipe to create trust.bin

# DEBUG: Replace serial driver and booti.c with debug versions
# Use absolute path for debug files (temporary debugging)
python do_patch:append() {
    import os
    import shutil
    import subprocess
    
    # Replace clk_rk3399.c with debug version
    debug_clk = "/home/xuning/yocto-rk3399/meta-rk3399-custom/recipes-bsp/u-boot/u-boot-rockchip/debug-files/clk_rk3399.c"
    target_clk = os.path.join(d.getVar('S'), 'drivers/clk/rockchip/clk_rk3399.c')
    
    if os.path.exists(debug_clk):
        bb.note("Replacing clk_rk3399.c with debug version from %s" % debug_clk)
        shutil.copy2(debug_clk, target_clk)
    else:
        bb.warn("Debug file not found: %s, using original clk_rk3399.c" % debug_clk)
    
    # Replace serial driver with debug version
    debug_file = "/home/xuning/yocto-rk3399/meta-rk3399-custom/recipes-bsp/u-boot/u-boot-rockchip/debug-files/serial_ns16550.c"
    target_file = os.path.join(d.getVar('S'), 'drivers/serial/serial_ns16550.c')
    
    if os.path.exists(debug_file):
        bb.note("Replacing serial_ns16550.c with debug version from %s" % debug_file)
        shutil.copy2(debug_file, target_file)
    else:
        bb.warn("Debug file not found: %s, using original serial driver" % debug_file)
    
    # Replace booti.c with debug version
    debug_booti = "/home/xuning/yocto-rk3399/meta-rk3399-custom/recipes-bsp/u-boot/u-boot-rockchip/debug-files/booti.c"
    target_booti = os.path.join(d.getVar('S'), 'cmd/booti.c')
    
    if os.path.exists(debug_booti):
        bb.note("Replacing booti.c with debug version from %s" % debug_booti)
        shutil.copy2(debug_booti, target_booti)
    else:
        bb.warn("Debug file not found: %s, using original booti.c" % debug_booti)
    
    # Replace autoboot.c with debug version
    debug_autoboot = "/home/xuning/yocto-rk3399/meta-rk3399-custom/recipes-bsp/u-boot/u-boot-rockchip/debug-files/autoboot.c"
    target_autoboot = os.path.join(d.getVar('S'), 'common/autoboot.c')
    
    if os.path.exists(debug_autoboot):
        bb.note("Replacing autoboot.c with debug version from %s" % debug_autoboot)
        shutil.copy2(debug_autoboot, target_autoboot)
    else:
        bb.warn("Debug file not found: %s, using original autoboot.c" % debug_autoboot)
    
    # Replace cpu-info.c with debug version
    debug_cpuinfo = "/home/xuning/yocto-rk3399/meta-rk3399-custom/recipes-bsp/u-boot/u-boot-rockchip/debug-files/cpu-info.c"
    target_cpuinfo = os.path.join(d.getVar('S'), 'arch/arm/mach-rockchip/cpu-info.c')
    
    if os.path.exists(debug_cpuinfo):
        bb.note("Replacing cpu-info.c with debug version from %s" % debug_cpuinfo)
        shutil.copy2(debug_cpuinfo, target_cpuinfo)
    else:
        bb.warn("Debug file not found: %s, using original cpu-info.c" % debug_cpuinfo)
    
    # Replace board_info.c with debug version
    debug_boardinfo = "/home/xuning/yocto-rk3399/meta-rk3399-custom/recipes-bsp/u-boot/u-boot-rockchip/debug-files/board_info.c"
    target_boardinfo = os.path.join(d.getVar('S'), 'common/board_info.c')
    
    if os.path.exists(debug_boardinfo):
        bb.note("Replacing board_info.c with debug version from %s" % debug_boardinfo)
        shutil.copy2(debug_boardinfo, target_boardinfo)
    else:
        bb.warn("Debug file not found: %s, using original board_info.c" % debug_boardinfo)
    
    # Replace board_f.c with debug version
    debug_boardf = "/home/xuning/yocto-rk3399/meta-rk3399-custom/recipes-bsp/u-boot/u-boot-rockchip/debug-files/board_f.c"
    target_boardf = os.path.join(d.getVar('S'), 'common/board_f.c')
    
    if os.path.exists(debug_boardf):
        bb.note("Replacing board_f.c with debug version from %s" % debug_boardf)
        shutil.copy2(debug_boardf, target_boardf)
    else:
        bb.warn("Debug file not found: %s, using original board_f.c" % debug_boardf)
    
    # Replace rk8xx.c with debug version
    debug_rk8xx = "/home/xuning/yocto-rk3399/meta-rk3399-custom/recipes-bsp/u-boot/u-boot-rockchip/debug-files/rk8xx.c"
    target_rk8xx = os.path.join(d.getVar('S'), 'drivers/power/pmic/rk8xx.c')
    
    if os.path.exists(debug_rk8xx):
        bb.note("Replacing rk8xx.c with debug version from %s" % debug_rk8xx)
        shutil.copy2(debug_rk8xx, target_rk8xx)
    else:
        bb.warn("Debug file not found: %s, using original rk8xx.c" % debug_rk8xx)
    
    # Replace board_r.c with debug version
    debug_boardr = "/home/xuning/yocto-rk3399/meta-rk3399-custom/recipes-bsp/u-boot/u-boot-rockchip/debug-files/board_r.c"
    target_boardr = os.path.join(d.getVar('S'), 'common/board_r.c')
    
    if os.path.exists(debug_boardr):
        bb.note("Replacing board_r.c with debug version from %s" % debug_boardr)
        shutil.copy2(debug_boardr, target_boardr)
    else:
        bb.warn("Debug file not found: %s, using original board_r.c" % debug_boardr)
    
    # Replace board.c with debug version
    debug_board = "/home/xuning/yocto-rk3399/meta-rk3399-custom/recipes-bsp/u-boot/u-boot-rockchip/debug-files/board.c"
    target_board = os.path.join(d.getVar('S'), 'arch/arm/mach-rockchip/board.c')
    
    if os.path.exists(debug_board):
        bb.note("Replacing board.c with debug version from %s" % debug_board)
        shutil.copy2(debug_board, target_board)
    else:
        bb.warn("Debug file not found: %s, using original board.c" % debug_board)
}

do_configure:prepend() {
    # Apply Rockchip-specific configuration
    if [ -f "${S}/configs/${UBOOT_MACHINE}" ]; then
        sed -i 's/CONFIG_USE_PREBOOT=y/# CONFIG_USE_PREBOOT is not set/' ${S}/configs/${UBOOT_MACHINE} || true
    fi
    
    # Copy Firefly device tree file to U-Boot source tree
    install -d ${S}/arch/arm/dts
    install -m 0644 ${WORKDIR}/rk3399-firefly.dts ${S}/arch/arm/dts/
}

do_compile:prepend() {
    # Verify that bl31.elf exists before compilation
    # This check provides a clear error message if the dependency wasn't deployed
    # The task dependency (do_compile[depends]) should ensure it's built, but this
    # provides a safety check and clearer error message
    if [ ! -f "${DEPLOY_DIR_IMAGE}/bl31.elf" ]; then
        bbfatal "bl31.elf not found in ${DEPLOY_DIR_IMAGE}/bl31.elf. " \
                "This usually happens after cleansstate. " \
                "Please run: bitbake arm-trusted-firmware-rk3399 -c deploy"
    fi
}

do_deploy:append() {
    # Deploy additional U-Boot images
    # Note: u-boot-dtb.bin is already deployed by base class u-boot.inc
    # Only deploy u-boot.itb which is not deployed by base class
    install -d ${DEPLOY_DIR_IMAGE}
    if [ -f "${B}/u-boot.itb" ]; then
        install -m 0644 ${B}/u-boot.itb ${DEPLOY_DIR_IMAGE}/u-boot.itb
    fi
}

FILES:${PN} = "/boot"

BBCLASSEXTEND = "native nativesdk"

