SUMMARY = "U-Boot bootloader for Rockchip RK3399"
DESCRIPTION = "U-Boot bootloader with Rockchip-specific patches and configuration"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://Licenses/gpl-2.0.txt;md5=b234ee4d69f5fce4486a80fdaf4a4263"

require recipes-bsp/u-boot/u-boot.inc
require u-boot-rockchip.inc

PROVIDES += "u-boot"

SRC_URI = "git://source.denx.de/u-boot/u-boot.git;protocol=https;branch=master"
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

do_configure:prepend() {
    # Apply Rockchip-specific configuration
    if [ -f "${S}/configs/${UBOOT_MACHINE}" ]; then
        sed -i 's/CONFIG_USE_PREBOOT=y/# CONFIG_USE_PREBOOT is not set/' ${S}/configs/${UBOOT_MACHINE} || true
    fi
}

do_deploy:append() {
    # Deploy U-Boot images
    install -d ${DEPLOY_DIR_IMAGE}
    install -m 0644 ${B}/u-boot.itb ${DEPLOY_DIR_IMAGE}/u-boot.itb
    install -m 0644 ${B}/u-boot-dtb.bin ${DEPLOY_DIR_IMAGE}/u-boot-dtb.bin
}

FILES:${PN} = "/boot"

BBCLASSEXTEND = "native nativesdk"

