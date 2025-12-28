# Boot image class for Rockchip RK3399
# Handles creation of boot partition with kernel, DTB, and boot script

BOOTIMG_ROOT_DIR = "${DEPLOY_DIR_IMAGE}"

# Create boot script in deploy directory
# This will be picked up by IMAGE_BOOT_FILES
do_create_boot_script[depends] += " \
    virtual/kernel:do_deploy \
    u-boot-rockchip:do_deploy \
    u-boot-tools-native:do_populate_sysroot \
"

do_create_boot_script() {
    # Find mkimage tool
    # Try U-Boot's mkimage from build directory first
    mkimage_cmd=""
    for dir in ${TMPDIR}/work-shared/*/u-boot-rockchip/*/build; do
        if [ -f "${dir}/tools/mkimage" ]; then
            mkimage_cmd="${dir}/tools/mkimage"
            bbnote "Using U-Boot's mkimage from work-shared: ${mkimage_cmd}"
            break
        fi
    done
    
    # Try work directory if not found
    if [ -z "${mkimage_cmd}" ]; then
        for dir in ${TMPDIR}/work/*/u-boot-rockchip/*/build; do
            if [ -f "${dir}/tools/mkimage" ]; then
                mkimage_cmd="${dir}/tools/mkimage"
                bbnote "Using U-Boot's mkimage from work: ${mkimage_cmd}"
                break
            fi
        done
    fi
    
    # Fallback to native sysroot
    if [ -z "${mkimage_cmd}" ]; then
        if [ -f "${STAGING_BINDIR_NATIVE}/mkimage" ]; then
            mkimage_cmd="${STAGING_BINDIR_NATIVE}/mkimage"
            bbnote "Using mkimage from native sysroot: ${mkimage_cmd}"
        fi
    fi
    
    if [ -z "${mkimage_cmd}" ] || [ ! -f "${mkimage_cmd}" ]; then
        bbfatal "mkimage tool not found. Please ensure u-boot-rockchip is built."
    fi
    
    # Create boot partition structure
    install -d ${BOOTIMG_ROOT_DIR}/boot
    
    # Create boot script
    cat > ${BOOTIMG_ROOT_DIR}/boot/boot.cmd << 'EOF'
# Boot script for RK3399
# Set load addresses (RK3399 has 2GB RAM, use safe addresses)
setenv ramdisk_addr_r "0x21000000"
setenv kernel_addr_r "0x00280000"
setenv fdt_addr_r "0x01f00000"
setenv load_addr "0x39000000"

# Set default values
setenv rootdev "/dev/mmcblk0p2"
setenv verbosity "1"
setenv console "both"
setenv rootfstype "ext4"

# Set device tree file name
setenv fdtfile "rk3399-firefly.dtb"

test -n "${distro_bootpart}" || distro_bootpart=1

echo "Boot script loaded from ${devtype} ${devnum}:${distro_bootpart}"

# Configure console
if test "${console}" = "display" || test "${console}" = "both"; then 
    setenv consoleargs "console=tty1"
fi
if test "${console}" = "serial" || test "${console}" = "both"; then 
    setenv consoleargs "console=ttyS2,1500000 ${consoleargs}"
fi

# Get partition UUID for rootfs
if test "${devtype}" = "mmc"; then 
    part uuid mmc ${devnum}:${distro_bootpart} partuuid
fi

# Set boot arguments
setenv bootargs "root=${rootdev} rootwait rootfstype=${rootfstype} ${consoleargs} consoleblank=0 loglevel=${verbosity}"

# Load kernel, device tree, and optional ramdisk
load ${devtype} ${devnum}:${distro_bootpart} ${kernel_addr_r} ${prefix}Image
load ${devtype} ${devnum}:${distro_bootpart} ${fdt_addr_r} ${prefix}dtb/${fdtfile}
# Try to load ramdisk, but continue if it doesn't exist
if load ${devtype} ${devnum}:${distro_bootpart} ${ramdisk_addr_r} ${prefix}uInitrd; then
    booti ${kernel_addr_r} ${ramdisk_addr_r} ${fdt_addr_r}
else
    echo "No ramdisk found, booting without initrd"
    booti ${kernel_addr_r} - ${fdt_addr_r}
fi
EOF
    
    # Compile boot script
    ${mkimage_cmd} \
        -C none \
        -A arm64 \
        -T script \
        -d ${BOOTIMG_ROOT_DIR}/boot/boot.cmd \
        ${BOOTIMG_ROOT_DIR}/boot/boot.scr || bbfatal "Failed to create boot.scr"
    
    bbnote "Created boot.scr and boot.cmd in ${BOOTIMG_ROOT_DIR}/boot/"
}

# Add task to image build process
addtask create_boot_script before do_image_complete after do_deploy

