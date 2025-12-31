# Boot image class for Rockchip RK3399
# Installs kernel and device tree to root filesystem /boot directory
# U-Boot bootcmd will load them directly

BOOTIMG_ROOT_DIR = "${DEPLOY_DIR_IMAGE}"
do_create_boot_script[depends] += " \
    virtual/kernel:do_deploy \
    u-boot-rockchip:do_deploy \
"

do_create_boot_script() {
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
    
    # Install kernel and device tree to root filesystem /boot directory
    # This is where U-Boot bootcmd will load them from
    install -d ${IMAGE_ROOTFS}/boot
    install -m 0644 ${DEPLOY_DIR_IMAGE}/${kernel_file} ${IMAGE_ROOTFS}/boot/Image-6.1.115
    install -m 0644 ${DEPLOY_DIR_IMAGE}/rk3399-firefly-aio.dtb ${IMAGE_ROOTFS}/boot/rk3399-firefly-aio.dtb
    bbnote "Installed kernel and DTB to ${IMAGE_ROOTFS}/boot/ for direct U-Boot bootcmd"
}

# Add task to image build process
# Run after rootfs is populated so we can install to IMAGE_ROOTFS
addtask create_boot_script before do_image_complete after do_rootfs
