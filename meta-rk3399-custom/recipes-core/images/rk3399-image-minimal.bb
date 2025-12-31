SUMMARY = "Minimal system image for RK3399"
DESCRIPTION = "Minimal system image for RK3399 platform with essential packages only"

inherit core-image
inherit rockchip-image
inherit bootimg-rockchip

# Base packages - minimal set
IMAGE_INSTALL = " \
    packagegroup-core-boot \
    kernel-modules \
    kernel-image \
    kernel-devicetree \
    ${CORE_IMAGE_EXTRA_INSTALL} \
"

# Bootloader
IMAGE_INSTALL += " \
    u-boot-rockchip \
    rk3399-blobs \
    arm-trusted-firmware-rk3399 \
"

# Ensure bootloader components are built before image generation
# This ensures all required files (bl31.elf, idbloader.bin, etc.) are available
do_image[depends] += " \
    u-boot-rockchip:do_deploy \
    rk3399-blobs:do_deploy \
    arm-trusted-firmware-rk3399:do_deploy \
"

# Also ensure dependencies for image_complete task
do_image_complete[depends] += " \
    u-boot-rockchip:do_deploy \
    rk3399-blobs:do_deploy \
    arm-trusted-firmware-rk3399:do_deploy \
"

# Force these dependencies to run before IMAGE_POSTPROCESS_COMMAND
addtask do_image_complete before do_image_complete_postprocess

# Add a pre-image task to ensure bootloader files are deployed
addtask deploy_bootloader before do_image_complete after do_install
do_deploy_bootloader() {
    bbnote "Deploying bootloader files for update.img generation..."
    
    # Deploy rk3399-blobs
    if [ -n "${DEPLOY_DIR_IMAGE}" ]; then
        install -d ${DEPLOY_DIR_IMAGE}
        
        # Find and copy bootloader files from work directories
        RK3399_BLOBS_WORK=$(find ${TMPDIR}/work/*/rk3399-blobs/*/image -name "*.bin" -o -name "*.img" 2>/dev/null | head -5)
        for file in $RK3399_BLOBS_WORK; do
            if [ -f "$file" ]; then
                cp -f "$file" ${DEPLOY_DIR_IMAGE}/
                bbnote "Deployed $(basename $file)"
            fi
        done
        
        # Find and copy ATF
        BL31_FILE=$(find ${TMPDIR}/work/*/arm-trusted-firmware-rk3399/*/image -name "bl31.elf" 2>/dev/null | head -1)
        if [ -f "$BL31_FILE" ]; then
            cp -f "$BL31_FILE" ${DEPLOY_DIR_IMAGE}/
            bbnote "Deployed bl31.elf"
        fi
        
        # Find and copy u-boot.env
        UBOOT_ENV=$(find ${TMPDIR}/work/*/u-boot-rockchip/*/image -name "uboot.env" 2>/dev/null | head -1)
        if [ -f "$UBOOT_ENV" ]; then
            cp -f "$UBOOT_ENV" ${DEPLOY_DIR_IMAGE}/
            bbnote "Deployed uboot.env"
        fi
    fi
}

# Image features - minimal
IMAGE_FEATURES += " \
    debug-tweaks \
"

# Root filesystem size (in KB, 0 = auto)
IMAGE_ROOTFS_SIZE ?= "1048576"

# Extra space for rootfs (in KB)
IMAGE_ROOTFS_EXTRA_SPACE ?= "32768"

# Image type
IMAGE_FSTYPES = "wic wic.bmap ext4"

# WIC file
WKS_FILE = "rk3399-sdimage.wks.in"

# Boot partition size (in MB)
BOOT_PARTITION_SIZE = "240"

# Boot files to install in boot partition (FAT partition, optional)
# Note: kernel and DTB are actually in root filesystem /boot directory
# boot.scr is also in root filesystem /boot directory, loaded by U-Boot bootcmd
# Boot partition may be empty or used for compatibility
IMAGE_BOOT_FILES = "Image rk3399-firefly-aio.dtb"

# Create .img file from .wic file (compatible with Armbian naming)
# This creates a .img file by copying the .wic file after all images are built
do_image_complete[postfuncs] += "create_img_from_wic"
create_img_from_wic() {
    # Create .img file by copying .wic file
    # This makes it compatible with Armbian's .img format
    local wic_file="${IMGDEPLOYDIR}/${IMAGE_NAME}${IMAGE_NAME_SUFFIX}.wic"
    local img_file="${IMGDEPLOYDIR}/${IMAGE_NAME}${IMAGE_NAME_SUFFIX}.img"
    local img_link="${IMGDEPLOYDIR}/${IMAGE_LINK_NAME}.img"
    
    if [ -f "${wic_file}" ]; then
        bbnote "Creating .img file from .wic file for Armbian compatibility"
        cp "${wic_file}" "${img_file}"
        # Also create symlink for convenience
        ln -sf "${IMAGE_NAME}${IMAGE_NAME_SUFFIX}.img" "${img_link}" || true
        bbnote "Created ${img_file} and ${img_link}"
    else
        bbwarn ".wic file not found: ${wic_file}"
    fi
}

