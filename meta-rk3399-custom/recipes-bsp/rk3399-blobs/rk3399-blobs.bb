SUMMARY = "Rockchip RK3399 bootloader binary blobs"
DESCRIPTION = "Binary blobs for RK3399 boot process: idbloader, uboot.img, trust.bin"
LICENSE = "Proprietary"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Proprietary;md5=0557f9d92cf58f2ccdd50f62f8ac0b28"

inherit deploy

DEPENDS = " \
    u-boot-rockchip \
    arm-trusted-firmware-rk3399 \
    rkbin-tools-native \
"

# Ensure U-Boot is compiled before we try to use its mkimage
do_compile[depends] += "u-boot-rockchip:do_compile"
RDEPENDS:${PN}:remove = "rkbin-tools-native"

S = "${WORKDIR}"

do_configure[noexec] = "1"

do_compile() {
    # Create idbloader.bin (DDR init blob + miniloader)
    # Format: mkimage -n rk3399 -T rksd -d <ddr_blob> idbloader.bin
    # Then append miniloader
    
    install -d ${B}
    
    # Get paths to blobs from rkbin-tools-native
    # Try to find blobs in sysroot
    DDR_BLOB=$(find ${STAGING_DIR_NATIVE}${datadir}/rkbin -name "rk3399_ddr*.bin" 2>/dev/null | head -1)
    MINILOADER_BLOB=$(find ${STAGING_DIR_NATIVE}${datadir}/rkbin -name "rk3399_miniloader*.bin" 2>/dev/null | head -1)
    
    if [ -z "${DDR_BLOB}" ] || [ ! -f "${DDR_BLOB}" ]; then
        bbfatal "DDR blob not found in ${STAGING_DIR_NATIVE}${datadir}/rkbin"
    fi
    if [ -z "${MINILOADER_BLOB}" ] || [ ! -f "${MINILOADER_BLOB}" ]; then
        bbfatal "Miniloader blob not found in ${STAGING_DIR_NATIVE}${datadir}/rkbin"
    fi
    BL31_BLOB="${DEPLOY_DIR_IMAGE}/bl31.elf"
    UBOOT_BIN="${DEPLOY_DIR_IMAGE}/u-boot-dtb.bin"
    
    # Create idbloader.bin using mkimage (required for RKDevTool compatibility)
    # Armbian uses: mkimage -n rk3399 -T rksd -d <ddr_blob> idbloader.bin
    # Then append miniloader
    # Note: rkbin-tools-native's mkimage should support rksd type for Rockchip
    if [ ! -f "${DDR_BLOB}" ]; then
        bbfatal "DDR blob not found: ${DDR_BLOB}"
    fi
    if [ ! -f "${MINILOADER_BLOB}" ]; then
        bbfatal "Miniloader blob not found: ${MINILOADER_BLOB}"
    fi
    
    # Use U-Boot's mkimage from source tree (supports rksd type for Rockchip)
    # This is the same approach Armbian uses: tools/mkimage -n rk3399 -T rksd
    # In Yocto, U-Boot's mkimage is built in the build directory
    # Try multiple locations where U-Boot might be built
    mkimage_cmd=""
    
    # Method 1: Find in work-shared (shared between recipes)
    for dir in ${TMPDIR}/work-shared/*/u-boot-rockchip/*/build; do
        if [ -f "${dir}/tools/mkimage" ]; then
            mkimage_cmd="${dir}/tools/mkimage"
            bbnote "Using U-Boot's mkimage from work-shared: ${mkimage_cmd}"
            break
        fi
    done
    
    # Method 2: Find in work directory (per-recipe build)
    if [ -z "${mkimage_cmd}" ]; then
        for dir in ${TMPDIR}/work/*/u-boot-rockchip/*/build; do
            if [ -f "${dir}/tools/mkimage" ]; then
                mkimage_cmd="${dir}/tools/mkimage"
                bbnote "Using U-Boot's mkimage from work: ${mkimage_cmd}"
                break
            fi
        done
    fi
    
    # Method 3: Try to find in git source directory (tools might be pre-built)
    if [ -z "${mkimage_cmd}" ]; then
        for dir in ${TMPDIR}/work-shared/*/u-boot-rockchip/*/git; do
            if [ -f "${dir}/tools/mkimage" ]; then
                mkimage_cmd="${dir}/tools/mkimage"
                bbnote "Using U-Boot's mkimage from git source: ${mkimage_cmd}"
                break
            fi
        done
    fi
    
    # Fallback to rkbin's mkimage (may not support rksd)
    if [ -z "${mkimage_cmd}" ]; then
        mkimage_cmd="${STAGING_BINDIR_NATIVE}/mkimage"
        bbwarn "U-Boot mkimage not found, using rkbin's mkimage (may not support rksd)"
    fi
    
    # Try to create idbloader.bin with mkimage rksd format (required for RKDevTool)
    # Armbian uses: mkimage -n rk3399 -T rksd -d <ddr_blob> idbloader.bin
    if ${mkimage_cmd} -T list 2>&1 | grep -q rksd; then
        # mkimage supports rksd, use it to create proper header
        ${mkimage_cmd} -n rk3399 -T rksd -d ${DDR_BLOB} ${B}/idbloader.bin || {
            bbwarn "mkimage rksd failed, using direct concatenation (may not work with RKDevTool)"
            cp ${DDR_BLOB} ${B}/idbloader.bin
        }
    else
        # mkimage doesn't support rksd, use direct concatenation
        # Note: This may not work with RKDevTool, but will work for dd-based flashing
        bbwarn "mkimage does not support rksd type, using direct concatenation (may not work with RKDevTool)"
        cp ${DDR_BLOB} ${B}/idbloader.bin
    fi
    
    # Append miniloader to idbloader.bin
    cat ${MINILOADER_BLOB} >> ${B}/idbloader.bin
    
    # Create uboot.img using loaderimage tool
    # Format: loaderimage --pack --uboot <u-boot.bin> uboot.img <offset>
    # Offset: 0x200000 (2097152 bytes = 4096 sectors)
    ${STAGING_BINDIR_NATIVE}/loaderimage \
        --pack \
        --uboot ${UBOOT_BIN} \
        ${B}/uboot.img \
        0x200000
    
    # Create trust.bin using trust_merger
    # This combines BL31 with trust configuration
    # Format matches Armbian's trust.ini structure
    cat > ${B}/trust.ini << EOF
[VERSION]
MAJOR=1
MINOR=0
[BL30_OPTION]
SEC=0
[BL31_OPTION]
SEC=1
PATH=bl31.elf
ADDR=0x10000
[BL32_OPTION]
SEC=0
[BL33_OPTION]
SEC=0
[OUTPUT]
PATH=trust.bin
EOF
    
    ${STAGING_BINDIR_NATIVE}/trust_merger \
        --replace bl31.elf ${BL31_BLOB} \
        ${B}/trust.ini
}

do_install() {
    install -d ${D}${datadir}/rk3399-blobs
    install -m 0644 ${B}/idbloader.bin ${D}${datadir}/rk3399-blobs/
    install -m 0644 ${B}/uboot.img ${D}${datadir}/rk3399-blobs/
    install -m 0644 ${B}/trust.bin ${D}${datadir}/rk3399-blobs/
}

do_deploy() {
    install -d ${DEPLOY_DIR_IMAGE}
    install -m 0644 ${B}/idbloader.bin ${DEPLOY_DIR_IMAGE}/
    install -m 0644 ${B}/uboot.img ${DEPLOY_DIR_IMAGE}/
    install -m 0644 ${B}/trust.bin ${DEPLOY_DIR_IMAGE}/
}

addtask deploy before do_build after do_install

FILES:${PN} = "${datadir}/rk3399-blobs"

