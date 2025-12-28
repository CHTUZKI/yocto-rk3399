# Boot image class for Rockchip RK3399
# Handles creation of boot partition with kernel, DTB, and boot script

inherit bootimg

BOOTIMG_ROOT_DIR = "${DEPLOY_DIR_IMAGE}"

do_bootimg[depends] += " \
    virtual/kernel:do_deploy \
    u-boot-rockchip:do_deploy \
"

bootimg_create_cmd() {
    # Create boot partition structure
    install -d ${BOOTIMG_ROOT_DIR}/boot
    
    # Copy kernel
    install -m 0644 ${DEPLOY_DIR_IMAGE}/${KERNEL_IMAGETYPE}-${KERNEL_VERSION} \
        ${BOOTIMG_ROOT_DIR}/boot/${KERNEL_IMAGETYPE}
    
    # Copy device tree
    install -d ${BOOTIMG_ROOT_DIR}/boot/dtb/rockchip
    install -m 0644 ${DEPLOY_DIR_IMAGE}/${KERNEL_DEVICETREE} \
        ${BOOTIMG_ROOT_DIR}/boot/dtb/${KERNEL_DEVICETREE}
    
    # Create boot script
    cat > ${BOOTIMG_ROOT_DIR}/boot/boot.cmd << 'EOF'
# Boot script for RK3399
setenv load_addr 0x9000000
setenv rootdev "/dev/mmcblk0p2"
setenv verbosity "1"
setenv console "both"
setenv rootfstype "ext4"

test -n "${distro_bootpart}" || distro_bootpart=1

echo "Boot script loaded from ${devtype} ${devnum}:${distro_bootpart}"

if test "${console}" = "display" || test "${console}" = "both"; then 
    setenv consoleargs "console=tty1"
fi
if test "${console}" = "serial" || test "${console}" = "both"; then 
    setenv consoleargs "console=ttyS2,1500000 ${consoleargs}"
fi

if test "${devtype}" = "mmc"; then 
    part uuid mmc ${devnum}:${distro_bootpart} partuuid
fi

setenv bootargs "root=${rootdev} rootwait rootfstype=${rootfstype} ${consoleargs} consoleblank=0 loglevel=${verbosity}"

load ${devtype} ${devnum}:${distro_bootpart} ${ramdisk_addr_r} ${prefix}uInitrd
load ${devtype} ${devnum}:${distro_bootpart} ${kernel_addr_r} ${prefix}Image
load ${devtype} ${devnum}:${distro_bootpart} ${fdt_addr_r} ${prefix}dtb/${fdtfile}

booti ${kernel_addr_r} ${ramdisk_addr_r} ${fdt_addr_r}
EOF
    
    # Compile boot script
    ${STAGING_BINDIR_NATIVE}/mkimage \
        -C none \
        -A arm64 \
        -T script \
        -d ${BOOTIMG_ROOT_DIR}/boot/boot.cmd \
        ${BOOTIMG_ROOT_DIR}/boot/boot.scr
}

python do_bootimg() {
    bootimg_create_cmd()
}

