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
    file://0001-rk3399-enable-stable-mac.patch \
    file://0002-rk3399-always-init-rkclk.patch \
    file://0003-rk3399-ehci-probe-usb2.patch \
    file://0004-rk3399-populate-child-node-of-syscon.patch \
    file://u-boot-rockchip.cfg \
"
# Default to Armbian-aligned mainline tag v2022.07 (locked commit)
SRCREV = "127ba75b48ab4ba0388c65b08251213d343c8d9c"

PV = "2022.07"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

# Rockchip specific patches
# These patches are from Armbian and ensure proper initialization

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
        # Disable USE_PREBOOT to avoid conflicts
        sed -i 's/CONFIG_USE_PREBOOT=y/# CONFIG_USE_PREBOOT is not set/' ${S}/configs/${UBOOT_MACHINE} || true
        
        # Set serial console baudrate to 1500000 for RK3399
        if ! grep -q "^CONFIG_BAUDRATE=" ${S}/configs/${UBOOT_MACHINE}; then
            echo "CONFIG_BAUDRATE=1500000" >> ${S}/configs/${UBOOT_MACHINE}
        else
            sed -i 's/^CONFIG_BAUDRATE=.*/CONFIG_BAUDRATE=1500000/' ${S}/configs/${UBOOT_MACHINE}
        fi
        
        # Enable Yocto standard distro boot for proper bootargs generation
        # This allows Yocto to dynamically generate correct bootargs
        sed -i 's/^# CONFIG_DISTRO_DEFAULTS is not set/CONFIG_DISTRO_DEFAULTS=y/' ${S}/configs/${UBOOT_MACHINE}
        
        # Use Yocto standard bootcmd - let distro boot handle everything
        # Remove any existing CONFIG_BOOTCOMMAND to use Yocto default
        sed -i '/^CONFIG_BOOTCOMMAND=/d' ${S}/configs/${UBOOT_MACHINE} || true
        
        # Let Yocto handle all environment variables automatically
        # Remove CONFIG_EXTRA_ENV_SETTINGS to use Yocto defaults
        sed -i '/^CONFIG_EXTRA_ENV_SETTINGS=/d' ${S}/configs/${UBOOT_MACHINE} || true
        
        # Don't set any hardcoded bootargs - let Yocto handle it
        # Yocto will generate correct environment variables automatically
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

