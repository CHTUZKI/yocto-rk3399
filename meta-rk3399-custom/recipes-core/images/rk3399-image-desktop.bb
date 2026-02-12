SUMMARY = "Desktop system image for RK3399 with XFCE desktop"
DESCRIPTION = "Desktop system image for RK3399 platform with standard XFCE desktop environment"

inherit core-image
inherit rockchip-image
inherit bootimg-rockchip
inherit rootfs_deb
inherit extrausers

# Set root password to "root"
# Password hash generated with: crypt.crypt('root', '$6$rootpwd123456')
# This hash represents password "root"
ROOT_PASSWORD_HASH = "\$6\$rootpwd123456\$A897rl/91mY42MpTZmGo25dgs60Gplk6u4BQ0Ux.i3h6IpkiI7.9q3y1nq50xTxVewytxtpo1kscJ03k/7HvA/"
EXTRA_USERS_PARAMS = "usermod -p '${ROOT_PASSWORD_HASH}' root;"

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

# Desktop Environment (standard XFCE)
IMAGE_INSTALL += " \
    packagegroup-xfce-base \
    packagegroup-xfce-extended \
"

# Disable screen saver, DPMS blanking, and set uniform desktop background
IMAGE_INSTALL += " \
    packagegroup-display-power-management \
"

# Static IP helper for RJ45 Ethernet (eth0 -> 192.168.137.5/24)
IMAGE_INSTALL += " \
    rk3399-static-ip \
"

# Real-time tuning at boot (governor/idle/scheduler)
IMAGE_INSTALL += " \
    rk3399-rt-tune \
"

# gnupg provides gpgv, required by apt for repository signature verification
IMAGE_INSTALL += " \
    gnupg \
"

# kmod provides lsmod (used by latency-histogram)
IMAGE_INSTALL += " \
    kmod \
"

# Debian archive keyring so apt-get update works without manual apt-key
IMAGE_INSTALL += " \
    debian-archive-keyring \
"

# Boot time sync from NTP so system time is correct (avoids "Release file is not valid yet")
IMAGE_INSTALL += " \
    ntpdate \
    rk3399-time-sync \
"

# LinuxCNC - CNC Machine Controller
# LinuxCNC is a free, open source CNC controller that can drive milling machines,
# lathes, 3D printers, laser cutters, plasma cutters, robot arms, and more.
# Requires PREEMPT_RT real-time kernel for proper operation.
IMAGE_INSTALL += " \
    packagegroup-linuxcnc \
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

# ------------------------------------------------------------------
# 配置板子上的 /etc/apt/sources.list 为常见的 Debian 源
# 注意：这是在 Poky/Yocto 系统上直接使用 Debian 仓库，可能存在 ABI 风险，
#       建议主要用于安装常用用户态工具，慎重升级核心库。
# ------------------------------------------------------------------
ROOTFS_POSTPROCESS_COMMAND += "configure_common_apt_sources; "

configure_common_apt_sources() {
    if [ -d "${IMAGE_ROOTFS}/etc/apt" ]; then
        cat > "${IMAGE_ROOTFS}/etc/apt/sources.list" << 'EOF'
deb http://deb.debian.org/debian bookworm main contrib non-free-firmware
deb http://deb.debian.org/debian-security bookworm-security main contrib non-free-firmware
deb http://deb.debian.org/debian bookworm-updates main contrib non-free-firmware
EOF

        install -d "${IMAGE_ROOTFS}/etc/apt/sources.list.d"
        cat > "${IMAGE_ROOTFS}/etc/apt/sources.list.d/bullseye.list" << 'EOF'
deb http://deb.debian.org/debian bullseye main contrib non-free
deb http://deb.debian.org/debian-security bullseye-security main contrib non-free
deb http://deb.debian.org/debian bullseye-updates main contrib non-free
EOF
    fi
}


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

