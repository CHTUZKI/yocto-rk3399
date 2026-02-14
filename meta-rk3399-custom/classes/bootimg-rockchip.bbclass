# Boot image class for Rockchip RK3399
# Installs kernel and device tree to root filesystem /boot directory
# U-Boot bootcmd will load them directly

BOOTIMG_ROOT_DIR = "${DEPLOY_DIR_IMAGE}"
do_create_boot_script[depends] += " \
    virtual/kernel:do_deploy \
    u-boot-rockchip:do_deploy \
"

do_create_boot_script() {
    if [ ! -f "${DEPLOY_DIR_IMAGE}/Image" ]; then
        bbfatal "Kernel image symlink not found in ${DEPLOY_DIR_IMAGE}/Image. Please ensure kernel is built first."
    fi
    
    # Install kernel and device tree to root filesystem /boot directory
    # This is where U-Boot bootcmd will load them from
    install -d ${IMAGE_ROOTFS}/boot
    install -m 0644 ${DEPLOY_DIR_IMAGE}/Image ${IMAGE_ROOTFS}/boot/Image
    install -m 0644 ${DEPLOY_DIR_IMAGE}/rk3399-firefly.dtb ${IMAGE_ROOTFS}/boot/rk3399-firefly.dtb
    bbnote "Installed kernel and DTB to ${IMAGE_ROOTFS}/boot/ for direct U-Boot bootcmd"
}

# Add task to image build process
# Run after rootfs is populated so we can install to IMAGE_ROOTFS
addtask create_boot_script before do_image_complete after do_rootfs
