SUMMARY = "Desktop system image for RK3399 with Armbian-style XFCE desktop"
DESCRIPTION = "Desktop system image for RK3399 platform with Armbian-style XFCE desktop environment"

inherit core-image
inherit rockchip-image
inherit bootimg-rockchip
inherit rootfs_deb

# Base packages
IMAGE_INSTALL = " \
    packagegroup-core-boot \
    kernel-modules \
    kernel-image \
    kernel-devicetree \
    htop \
    ${CORE_IMAGE_EXTRA_INSTALL} \
"

# Bootloader
IMAGE_INSTALL += " \
    u-boot-rockchip \
    rk3399-blobs \
    arm-trusted-firmware-rk3399 \
"

# Desktop Environment
IMAGE_INSTALL += " \
    packagegroup-armbian-desktop-xfce \
"

# Ensure bootloader components are built before image generation
do_image[depends] += " \
    u-boot-rockchip:do_deploy \
    rk3399-blobs:do_deploy \
    arm-trusted-firmware-rk3399:do_deploy \
"

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

# Image features - desktop
IMAGE_FEATURES += " \
    debug-tweaks \
    ssh-server-openssh \
    x11-base \
    x11-sato \
    package-management \
"


# Root filesystem size (larger for desktop)
IMAGE_ROOTFS_SIZE ?= "3145728"

# Extra space for rootfs
IMAGE_ROOTFS_EXTRA_SPACE ?= "131072"

# Image type
IMAGE_FSTYPES = "wic wic.bmap ext4"

# WIC file
WKS_FILE = "rk3399-sdimage.wks.in"

# Boot partition size (in MB)
BOOT_PARTITION_SIZE = "240"

# Boot files to install in boot partition
IMAGE_BOOT_FILES = "Image rk3399-evb.dtb"

# Create .img file from .wic file (compatible with Armbian naming)
do_image_complete[postfuncs] += "create_img_from_wic"
create_img_from_wic() {
    local wic_file="${IMGDEPLOYDIR}/${IMAGE_NAME}${IMAGE_NAME_SUFFIX}.wic"
    local img_file="${IMGDEPLOYDIR}/${IMAGE_NAME}${IMAGE_NAME_SUFFIX}.img"
    local img_link="${IMGDEPLOYDIR}/${IMAGE_LINK_NAME}.img"
    
    if [ -f "${wic_file}" ]; then
        bbnote "Creating .img file from .wic file for Armbian compatibility"
        cp "${wic_file}" "${img_file}"
        ln -sf "${IMAGE_NAME}${IMAGE_NAME_SUFFIX}.img" "${img_link}" || true
        bbnote "Created ${img_file} and ${img_link}"
    else
        bbwarn ".wic file not found: ${wic_file}"
    fi
}

