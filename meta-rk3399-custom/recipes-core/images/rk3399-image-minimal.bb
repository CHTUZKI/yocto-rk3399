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
"

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

# Boot files to install in boot partition
# RK3399 uses Image (uncompressed kernel), device tree, and boot script
# Device tree file name without path prefix
# boot.scr and boot.cmd are in boot/ subdirectory
IMAGE_BOOT_FILES = "Image rk3399-firefly.dtb boot/boot.scr boot/boot.cmd"

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

