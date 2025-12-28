SUMMARY = "Complete system image for RK3399"
DESCRIPTION = "Full-featured system image for RK3399 platform"

inherit core-image

# Base packages
IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-base-extended \
    kernel-modules \
    kernel-image \
    kernel-devicetree \
    ${CORE_IMAGE_EXTRA_INSTALL} \
"

# System utilities
IMAGE_INSTALL += " \
    systemd \
    udev \
    networkmanager \
    wpa-supplicant \
    iptables \
"

# Development tools (optional)
IMAGE_INSTALL += " \
    gcc \
    g++ \
    make \
    git \
    vim \
    nano \
"

# Rockchip specific
IMAGE_INSTALL += " \
    rockchip-mpp \
    mesa \
"

# Bootloader
IMAGE_INSTALL += " \
    u-boot-rockchip \
    rk3399-blobs \
"

# Image features
IMAGE_FEATURES += " \
    ssh-server-dropbear \
    package-management \
    debug-tweaks \
"

# Root filesystem size (in KB, 0 = auto)
IMAGE_ROOTFS_SIZE ?= "2097152"

# Extra space for rootfs (in KB)
IMAGE_ROOTFS_EXTRA_SPACE ?= "65536"

# Image type
IMAGE_FSTYPES = "wic wic.bmap ext4"

# WIC file
WKS_FILE = "rk3399-sdimage.wks.in"

# Boot partition size (in MB)
BOOT_PARTITION_SIZE = "240"

# Root partition starts after boot partition
# Boot: 240MB, Root: rest of image

