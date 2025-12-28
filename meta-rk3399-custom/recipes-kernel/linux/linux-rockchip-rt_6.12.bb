SUMMARY = "Linux kernel for Rockchip RK3399"
DESCRIPTION = "Linux kernel from Armbian for RK3399 platform"

require recipes-kernel/linux/linux-yocto.inc

LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

# Kernel version
LINUX_VERSION = "6.1"
LINUX_VERSION_EXTENSION = "-rk5.1"

# Kernel source - Armbian linux-rockchip
KBRANCH = "rk-6.1-rkr5.1"
SRCREV = "${AUTOREV}"

SRC_URI = " \
    git://github.com/armbian/linux-rockchip.git;branch=${KBRANCH};protocol=https \
    file://defconfig \
    file://rk3399-firefly.dts \
"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

KERNEL_CONFIG_FRAGMENTS += "${WORKDIR}/defconfig"

# Rockchip specific features
KERNEL_FEATURES:append = " \
    cfg/fs/vfat.scc \
    cfg/fs/ext4.scc \
"

# Device tree
KERNEL_DEVICETREE = "rockchip/rk3399-firefly.dtb"

COMPATIBLE_MACHINE = "rk3399-.*"

# Use Armbian's kernel configuration approach
do_configure:prepend() {
    # Use defconfig if provided
    if [ -f "${WORKDIR}/defconfig" ]; then
        cp ${WORKDIR}/defconfig ${B}/.config
    fi
}

KERNEL_EXTRA_ARGS += " \
    LOADADDR=0x00200000 \
"

# Module signing (optional)
KERNEL_MODULE_SIGNING_ENABLE ?= "0"

# Kernel image type
KERNEL_IMAGETYPE = "Image"
KERNEL_IMAGETYPES = "Image"

# Output files
KERNEL_OUTPUT = "arch/${ARCH}/boot/${KERNEL_IMAGETYPE}"
KERNEL_OUTPUT_DIR = "${B}"

# Deploy
do_deploy:append() {
    install -d ${DEPLOY_DIR_IMAGE}
    install -m 0644 ${B}/${KERNEL_OUTPUT} ${DEPLOY_DIR_IMAGE}/${KERNEL_IMAGETYPE}-${KERNEL_VERSION}
    install -m 0644 ${B}/arch/${ARCH}/boot/dts/rockchip/rk3399-firefly.dtb ${DEPLOY_DIR_IMAGE}/
}

FILES:${KERNEL_PACKAGE_NAME}-base = ""
FILES:${KERNEL_PACKAGE_NAME}-image = "/boot/${KERNEL_IMAGETYPE}*"

RDEPENDS:${KERNEL_PACKAGE_NAME}-base = ""
RRECOMMENDS:${KERNEL_PACKAGE_NAME}-base = ""

python __anonymous() {
    kerneltype = d.getVar("KERNEL_IMAGETYPE", True)
    if kerneltype == "Image":
        d.setVar("KERNEL_IMAGETYPE_FOR_MAKE", "Image")
}

