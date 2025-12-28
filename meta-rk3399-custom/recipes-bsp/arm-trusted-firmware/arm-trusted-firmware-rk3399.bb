SUMMARY = "ARM Trusted Firmware for Rockchip RK3399"
DESCRIPTION = "ARM Trusted Firmware (ATF) binary blob for RK3399"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/BSD-3-Clause;md5=550794465ba0ec5312d6919e203a55f9"

inherit deploy

# Use prebuilt BL31 from rkbin
DEPENDS = "rkbin-tools-native"

S = "${WORKDIR}"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${datadir}/rkbin
    
    # Find BL31 blob from rkbin-tools-native (try v1.36 first, then v1.35)
    BL31_SRC="${STAGING_DIR_NATIVE}${datadir}/rkbin/rk33/rk3399_bl31_v1.36.elf"
    if [ ! -f "${BL31_SRC}" ]; then
        BL31_SRC="${STAGING_DIR_NATIVE}${datadir}/rkbin/rk33/rk3399_bl31_v1.35.elf"
    fi
    
    # Check if file exists, if not try alternative locations
    if [ ! -f "${BL31_SRC}" ]; then
        # Try to find it using wildcard
        BL31_SRC=$(find ${STAGING_DIR_NATIVE}${datadir}/rkbin -name "rk3399_bl31*.elf" 2>/dev/null | head -1)
        if [ -z "${BL31_SRC}" ] || [ ! -f "${BL31_SRC}" ]; then
            bbfatal "Cannot find rk3399_bl31*.elf. Please ensure rkbin-tools-native is built successfully."
        fi
    fi
    
    install -m 0644 "${BL31_SRC}" ${D}${datadir}/rkbin/bl31.elf
}

do_deploy() {
    install -d ${DEPLOY_DIR_IMAGE}
    install -m 0644 ${D}${datadir}/rkbin/bl31.elf ${DEPLOY_DIR_IMAGE}/bl31.elf
}

addtask deploy before do_build after do_install

FILES:${PN} = "${datadir}/rkbin/bl31.elf"

