SUMMARY = "Linux kernel for Rockchip RK3399"
DESCRIPTION = "Linux kernel from Armbian for RK3399 platform"

require recipes-kernel/linux/linux-yocto.inc

LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

# Kernel version
# Note: Actual kernel version is 6.1.115, but we use 6.1 for recipe version
LINUX_VERSION = "6.1"
LINUX_VERSION_EXTENSION = "-rk5.1"
# Skip version sanity check since Armbian kernel uses different versioning
KERNEL_VERSION_SANITY_SKIP = "1"

# Kernel source - Armbian linux-rockchip
KBRANCH = "rk-6.1-rkr5.1"
SRCREV = "${AUTOREV}"

SRC_URI = " \
    git://github.com/armbian/linux-rockchip.git;branch=${KBRANCH};protocol=https \
    file://defconfig \
    file://rk3399-firefly-aio.dts \
    file://rk3399-firefly-port.dtsi \
    file://rk3399-firefly-core.dtsi \
"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

KERNEL_CONFIG_FRAGMENTS += "${WORKDIR}/defconfig"

# Armbian kernel doesn't use Yocto kernel features system
# Remove KERNEL_FEATURES to avoid errors
KERNEL_FEATURES = ""
KERNEL_DANGLING_FEATURES_WARN_ONLY = "1"

# Device tree
KERNEL_DEVICETREE = "rockchip/rk3399-firefly-aio.dtb"

COMPATIBLE_MACHINE = "rk3399-.*"

# Copy device tree files to kernel source
do_configure:prepend() {
    # Use defconfig if provided
    if [ -f "${WORKDIR}/defconfig" ]; then
        cp ${WORKDIR}/defconfig ${B}/.config
    fi
    
    # Copy device tree files to kernel source tree
    install -d ${S}/arch/arm64/boot/dts/rockchip
    install -m 0644 ${WORKDIR}/rk3399-firefly-aio.dts ${S}/arch/arm64/boot/dts/rockchip/
    install -m 0644 ${WORKDIR}/rk3399-firefly-port.dtsi ${S}/arch/arm64/boot/dts/rockchip/
    install -m 0644 ${WORKDIR}/rk3399-firefly-core.dtsi ${S}/arch/arm64/boot/dts/rockchip/
}

KERNEL_EXTRA_ARGS += " \
    LOADADDR=0x00200000 \
"

# Module signing (optional)
KERNEL_MODULE_SIGNING_ENABLE ?= "0"

# Kernel image type
KERNEL_IMAGETYPE = "Image"
KERNEL_IMAGETYPES = "Image"

# Output files - kernel image is in arch/arm64/boot/Image
KERNEL_OUTPUT = "${KERNEL_IMAGETYPE}"
KERNEL_OUTPUT_DIR = "${B}/arch/${ARCH}/boot"

# Override install to handle Armbian kernel structure
do_install() {
    # Install modules if enabled
    if (grep -q -i -e '^CONFIG_MODULES=y$' ${B}/.config); then
        oe_runmake DEPMOD=echo MODLIB=${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION} INSTALL_FW_PATH=${D}${nonarch_base_libdir}/firmware INSTALL_MOD_PATH=${D} modules_install
        rm -f "${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/build"
        rm -f "${D}${nonarch_base_libdir}/modules/${KERNEL_VERSION}/source"
    fi
    
    # Install kernel image
    install -d ${D}/${KERNEL_IMAGEDEST}
    install -m 0644 ${B}/arch/${ARCH}/boot/${KERNEL_IMAGETYPE} ${D}/${KERNEL_IMAGEDEST}/${KERNEL_IMAGETYPE}-${KERNEL_VERSION}
    
    # Install other kernel files
    install -m 0644 ${B}/System.map ${D}/${KERNEL_IMAGEDEST}/System.map-${KERNEL_VERSION}
    install -m 0644 ${B}/.config ${D}/${KERNEL_IMAGEDEST}/config-${KERNEL_VERSION}
    [ -e ${B}/vmlinux ] && install -m 0644 ${B}/vmlinux ${D}/${KERNEL_IMAGEDEST}/vmlinux-${KERNEL_VERSION}
    [ -e ${B}/Module.symvers ] && install -m 0644 ${B}/Module.symvers ${D}/${KERNEL_IMAGEDEST}/Module.symvers-${KERNEL_VERSION}
    
    # Install device tree
    install -d ${D}/boot/dtb/rockchip
    install -m 0644 ${B}/arch/${ARCH}/boot/dts/rockchip/rk3399-firefly-aio.dtb ${D}/boot/dtb/rockchip/
    
    install -d ${D}${sysconfdir}/modules-load.d
    install -d ${D}${sysconfdir}/modprobe.d
}

# Deploy
do_deploy:append() {
    install -d ${DEPLOY_DIR_IMAGE}
    install -m 0644 ${B}/arch/${ARCH}/boot/${KERNEL_IMAGETYPE} ${DEPLOY_DIR_IMAGE}/${KERNEL_IMAGETYPE}-${KERNEL_VERSION}
    [ -e ${B}/arch/${ARCH}/boot/dts/rockchip/rk3399-firefly-aio.dtb ] && install -m 0644 ${B}/arch/${ARCH}/boot/dts/rockchip/rk3399-firefly-aio.dtb ${DEPLOY_DIR_IMAGE}/
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

