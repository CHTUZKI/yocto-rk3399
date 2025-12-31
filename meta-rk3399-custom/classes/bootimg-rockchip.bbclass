# Boot image class for Rockchip RK3399
# Creates boot.scr script and installs it to root filesystem /boot directory
# boot.scr contains all boot logic and is loaded by U-Boot's fixed bootcmd

BOOTIMG_ROOT_DIR = "${DEPLOY_DIR_IMAGE}"
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
    
    # Create directory for boot script
    install -d ${BOOTIMG_ROOT_DIR}/boot
    
    # Find kernel image file name from deploy directory
    # Look for Image-* files (kernel with version number)
    kernel_file=""
    
    # Priority 1: Try Image-${KERNEL_VERSION} format (e.g., Image-6.1.115)
    if [ -n "${KERNEL_VERSION}" ] && [ -f "${DEPLOY_DIR_IMAGE}/Image-${KERNEL_VERSION}" ]; then
        kernel_file="Image-${KERNEL_VERSION}"
        bbnote "Found kernel file using KERNEL_VERSION: ${kernel_file}"
    else
        # Priority 2: Try to find Image-X.Y.Z format (simple version number, not Yocto build name)
        # Exclude files with Yocto build suffixes like "-r0-rk3399-firefly-..."
        img_found=$(ls -1 ${DEPLOY_DIR_IMAGE}/Image-* 2>/dev/null | grep -E "Image-[0-9]+\.[0-9]+\.[0-9]+$" | sort -V | tail -1)
        if [ -n "${img_found}" ] && [ -f "${img_found}" ]; then
            kernel_file=$(basename ${img_found})
            bbnote "Found kernel file with version number: ${kernel_file}"
        else
            # Priority 3: Try any Image-* file (fallback)
            img_found=$(ls -1 ${DEPLOY_DIR_IMAGE}/Image-* 2>/dev/null | grep -v "\.bin$" | sort -V | tail -1)
            if [ -n "${img_found}" ] && [ -f "${img_found}" ]; then
                kernel_file=$(basename ${img_found})
                bbnote "Found kernel file by scanning: ${kernel_file}"
            fi
        fi
    fi
    
    # Fallback to Image if no versioned file found
    if [ -z "${kernel_file}" ]; then
        if [ -f "${DEPLOY_DIR_IMAGE}/Image" ]; then
            kernel_file="Image"
            bbnote "Using kernel file without version: ${kernel_file}"
        else
            bbfatal "Kernel image file not found in ${DEPLOY_DIR_IMAGE}. Please ensure kernel is built first."
        fi
    fi
    
    # Create boot script - all boot logic here, simple and clean
    # GPT partition layout: 0=uboot, 1=trust, 2=boot(FAT), 3=root(ext4)
    # U-Boot mmc numbering: 0:2=boot(FAT), 0:3=root(ext4)
    # Linux device mapping: p1=boot, p2=root (typically)
    cat > ${BOOTIMG_ROOT_DIR}/boot/boot.cmd << EOF
# Boot script for RK3399 Firefly
# All boot logic is here - simple and maintainable

echo "=========================================="
echo "RK3399 Boot Script Starting"
echo "=========================================="

# Initialize MMC
mmc dev 0
mmc rescan

# Show MMC info
echo ""
echo "MMC Device Info:"
mmc info

# Set load addresses
setenv kernel_addr_r 0x00280000
setenv fdt_addr_r 0x03000000
setenv scriptaddr 0x00500000

# Show GPT partition table
echo ""
echo "GPT Partition Table:"
part list mmc 0

# Check if boot partition exists
echo ""
echo "Checking boot partition (mmc 0:3)..."
if part size mmc 0 3; then
    echo "Boot partition found, size: \$(part size mmc 0 3) sectors"
else
    echo "ERROR: Boot partition not found!"
    exit
fi

# Detect root filesystem partition
echo ""
echo "Detecting root filesystem partition..."
if ext4load mmc 0:3 \${kernel_addr_r} /boot/Image-6.1.115; then
    echo "Loading kernel: Image-6.1.115"
    if ext4load mmc 0:3 \${fdt_addr_r} /boot/rk3399-firefly-aio.dtb; then
        echo "Loading device tree: rk3399-firefly-aio.dtb"
        setenv bootargs "earlycon=uart8250,mmio32,0xff1a0000,1500000n8 root=/dev/mmcblk0p4 rootwait rootfstype=ext4 console=ttyS2,1500000 consoleblank=0 loglevel=7"
        echo "Boot arguments: \${bootargs}"
        echo "Booting kernel..."
        echo ""
        booti \${kernel_addr_r} - \${fdt_addr_r}
    else
        echo "ERROR: Failed to load device tree"
    fi
else
    echo "ERROR: Failed to load kernel"
fi
EOF
    
    # Compile boot script
    ${mkimage_cmd} \
        -A arm64 \
        -T script \
        -d ${BOOTIMG_ROOT_DIR}/boot/boot.cmd \
        ${BOOTIMG_ROOT_DIR}/boot/boot.scr || bbfatal "Failed to create boot.scr"
    
    bbnote "Created boot.scr and boot.cmd in ${BOOTIMG_ROOT_DIR}/boot/"
    
    # Install boot.scr to root filesystem /boot directory
    # This is where U-Boot bootcmd will load it from
    install -d ${IMAGE_ROOTFS}/boot
    install -m 0644 ${BOOTIMG_ROOT_DIR}/boot/boot.scr ${IMAGE_ROOTFS}/boot/boot.scr
    install -m 0644 ${BOOTIMG_ROOT_DIR}/boot/boot.cmd ${IMAGE_ROOTFS}/boot/boot.cmd
    bbnote "Installed boot.scr to ${IMAGE_ROOTFS}/boot/ for U-Boot bootcmd"
}

# Add task to image build process
# Run after rootfs is populated so we can install to IMAGE_ROOTFS
addtask create_boot_script before do_image_complete after do_rootfs

