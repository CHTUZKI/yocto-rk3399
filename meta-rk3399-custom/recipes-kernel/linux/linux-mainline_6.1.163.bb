SUMMARY = "Mainline Linux kernel"
DESCRIPTION = "Linux kernel from kernel.org (mainline stable tarball)"

LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

inherit kernel

PV = "6.1.163"

FILESEXTRAPATHS:prepend := "${THISDIR}/linux-mainline:"

SRC_URI = "${KERNELORG_MIRROR}/linux/kernel/v6.x/linux-${PV}.tar.xz \
           file://defconfig \
           file://rk3399-firefly.dts \
          "

SRC_URI[sha256sum] = "fd2d033321bd15e0ad5669208b6e43f3f93ccecb059a512ca6b913ca940c38ea"

S = "${WORKDIR}/linux-${PV}"

do_configure:append() {
    if [ -f "${WORKDIR}/rk3399-firefly.dts" ]; then
        install -d ${S}/arch/arm64/boot/dts/rockchip
        install -m 0644 ${WORKDIR}/rk3399-firefly.dts ${S}/arch/arm64/boot/dts/rockchip/rk3399-firefly.dts
    fi
}

KERNEL_IMAGETYPE = "Image"
KERNEL_IMAGETYPES = "Image"

COMPATIBLE_MACHINE = "rk3399-.*"
