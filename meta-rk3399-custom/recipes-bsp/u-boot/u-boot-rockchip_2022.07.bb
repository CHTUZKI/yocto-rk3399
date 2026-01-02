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
        
        # Disable distro boot - use simple fixed bootcmd instead
        # This keeps boot logic in boot.scr only, not scattered across env/distro/scripts
        sed -i 's/^CONFIG_DISTRO_DEFAULTS=y/# CONFIG_DISTRO_DEFAULTS is not set/' ${S}/configs/${UBOOT_MACHINE} || true
        
        # Set fixed bootcmd: load boot.scr from root partition /boot directory and execute it
        # GPT partition 3 (root ext4) = U-Boot mmc 0:4 (Linux mmcblk0p4)
        # boot.scr is installed to root filesystem /boot/boot.scr
        # Try direct commands instead of script to avoid format issues
        sed -i '/^CONFIG_BOOTCOMMAND=/d' ${S}/configs/${UBOOT_MACHINE} || true
        echo 'CONFIG_BOOTCOMMAND="echo \"=== U-Boot Debug: Direct boot mode ===\"; mmc dev 0; mmc rescan; echo \"=== U-Boot Debug: MMC Info ===\"; mmc info; echo \"=== U-Boot Debug: Partition Table ===\"; part list mmc 0; echo \"=== U-Boot Debug: Filesystem Check ===\"; ext4ls mmc 0:4 /boot && echo \"Boot directory accessible\" || echo \"ERROR: Cannot access boot directory\"; echo \"=== U-Boot Debug: Loading kernel ===\"; ext4load mmc 0:4 0x00280000 /boot/Image-6.1.115 && echo \"=== U-Boot Debug: Kernel loaded (size: \${filesize} bytes) ===\" && ext4load mmc 0:4 0x03000000 /boot/rk3399-firefly.dtb && echo \"=== U-Boot Debug: Device tree loaded (size: \${filesize} bytes) ===\" && setenv bootargs \"root=/dev/mmcblk0p4 rootwait rootfstype=ext4 console=ttyS2,1500000 earlycon=uart8250,mmio32,0xff1a0000,1500000n8 loglevel=7 debug deferred_probe_timeout=30\" && echo \"=== U-Boot Debug: Boot args set ===\"; echo \"=== U-Boot Debug: Booting kernel ===\"; booti 0x00280000 - 0x03000000 || echo \"=== U-Boot Debug: Direct boot failed ===\""' >> ${S}/configs/${UBOOT_MACHINE}
        
        # Force environment reset on first boot to ensure our bootcmd is used
        echo 'CONFIG_ENV_IS_NOWHERE=y' >> ${S}/configs/${UBOOT_MACHINE}
        echo '# CONFIG_ENV_IS_IN_MMC is not set' >> ${S}/configs/${UBOOT_MACHINE}
        
        # Override environment variables to ensure bootcmd is used
        # Create uboot.env with correct bootcmd that loads boot.scr
        echo 'bootcmd=mmc dev 0; mmc rescan; ext4load mmc 0:3 0x00500000 /boot/boot.scr; source 0x00500000' > ${WORKDIR}/uboot.env.txt
        echo 'bootargs=earlycon=uart8250,mmio32,0xff1a0000,1500000n8 root=/dev/mmcblk0p4 rootwait rootfstype=ext4 console=ttyS2,1500000 consoleblank=0 loglevel=7' >> ${WORKDIR}/uboot.env.txt
        
        # Enable script support and legacy image format
        # Use direct CONFIG defines instead of relying on CONFIG_IS_ENABLED
        if ! grep -q "^CONFIG_BOOT_SCRIPT=y" ${S}/configs/${UBOOT_MACHINE}; then
            echo "CONFIG_BOOT_SCRIPT=y" >> ${S}/configs/${UBOOT_MACHINE}
        fi
        if ! grep -q "^CONFIG_CMD_SOURCE=y" ${S}/configs/${UBOOT_MACHINE}; then
            echo "CONFIG_CMD_SOURCE=y" >> ${S}/configs/${UBOOT_MACHINE}
        fi
        if ! grep -q "^CONFIG_LEGACY_IMAGE_FORMAT=y" ${S}/configs/${UBOOT_MACHINE}; then
            echo "CONFIG_LEGACY_IMAGE_FORMAT=y" >> ${S}/configs/${UBOOT_MACHINE}
        fi
        if ! grep -q "^CONFIG_IMAGE_FORMAT_LEGACY=y" ${S}/configs/${UBOOT_MACHINE}; then
            echo "CONFIG_IMAGE_FORMAT_LEGACY=y" >> ${S}/configs/${UBOOT_MACHINE}
        fi
        if ! grep -q "^CONFIG_FIT=y" ${S}/configs/${UBOOT_MACHINE}; then
            echo "# CONFIG_FIT is not set" >> ${S}/configs/${UBOOT_MACHINE}
        fi
        
        # Copy Firefly device tree file to U-Boot source tree
        install -d ${S}/arch/arm/dts
        install -m 0644 ${WORKDIR}/rk3399-firefly.dts ${S}/arch/arm/dts/
    fi
}

do_compile:append() {
    # Skip env generation here - will be done in do_install
}

do_install:append() {
    # Install uboot.env to deploy directory for image creation
    install -d ${DEPLOYDIR}
    
    # Generate uboot.env binary using mkenvimage tool from host tools
    if [ -f "${WORKDIR}/uboot.env.txt" ]; then
        bbnote "Found uboot.env.txt, generating uboot.env binary"
        # Try to use mkenvimage from u-boot-tools-native or build tools
        mkenvimage -s ${UBOOT_ENV_SIZE} -o ${WORKDIR}/uboot.env ${WORKDIR}/uboot.env.txt 2>/dev/null || \
        ${S}/build/tools/mkenvimage -s ${UBOOT_ENV_SIZE} -o ${WORKDIR}/uboot.env ${WORKDIR}/uboot.env.txt 2>/dev/null || \
        cp ${WORKDIR}/uboot.env.txt ${WORKDIR}/uboot.env
    else
        bbfatal "uboot.env.txt not found in ${WORKDIR}"
    fi
    
    if [ -f "${WORKDIR}/uboot.env" ]; then
        install -m 0644 ${WORKDIR}/uboot.env ${DEPLOYDIR}/uboot.env
        bbnote "Installed uboot.env to ${DEPLOYDIR}/uboot.env"
    else
        bbfatal "uboot.env not found in ${WORKDIR} after generation"
    fi
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

