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
    
    # Create boot script with kernel filename embedded
    cat > ${BOOTIMG_ROOT_DIR}/boot/boot.cmd << EOF
# Boot script for RK3399 - Load from root partition
# This script loads kernel and DTB from root partition (ext4)
echo "[BOOTSCRIPT] Starting RK3399 boot script"

# Set load addresses (RK3399 has 2GB RAM, use safe addresses)
setenv ramdisk_addr_r "0x28000000"
setenv kernel_addr_r "0x00280000"
setenv fdt_addr_r "0x03000000"

# Set default values
setenv rootdev "/dev/mmcblk0p3"
setenv rootpart "3"
setenv verbosity "7"
setenv rootfstype="ext4"

# Set device tree file name
setenv fdtfile "rk3399-firefly-aio.dtb"

# Set kernel file name (embedded at build time)
setenv kernel_image "${kernel_file}"

echo "[BOOTSCRIPT] Loading kernel from root partition (ext4)"

# Set boot arguments
# Add earlycon for early kernel console output
# RK3399 UART2 (ttyS2) base address is 0xff1a0000
setenv bootargs "earlycon=uart8250,mmio32,0xff1a0000,1500000n8 root=\${rootdev} rootwait rootfstype=\${rootfstype} console=ttyS2,1500000 consoleblank=0 loglevel=\${verbosity}"

# Load kernel from root partition (ext4 filesystem)
echo "[BOOTSCRIPT] Loading kernel: \${kernel_image}"
if ext4load mmc 0:3 \${kernel_addr_r} /boot/\${kernel_image}; then
    echo "[BOOTSCRIPT] Kernel loaded successfully"
else
    echo "[BOOTSCRIPT] ERROR: Could not load kernel \${kernel_image}"
    echo "[BOOTSCRIPT] Available files in /boot directory:"
    ext4ls mmc 0:3 /boot
    exit
fi

# Load device tree from root partition
echo "[BOOTSCRIPT] Loading DTB: \${fdtfile}"
if ext4load mmc 0:3 \${fdt_addr_r} /boot/\${fdtfile}; then
    echo "[BOOTSCRIPT] DTB loaded successfully"
else
    echo "[BOOTSCRIPT] ERROR: DTB file \${fdtfile} not found"
    exit
fi

# Boot the kernel using standard ARM64 boot
echo "[BOOTSCRIPT] bootargs: \${bootargs}"
booti \${kernel_addr_r} - \${fdt_addr_r}
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

