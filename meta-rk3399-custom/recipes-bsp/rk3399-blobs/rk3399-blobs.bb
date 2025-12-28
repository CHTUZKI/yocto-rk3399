SUMMARY = "Rockchip RK3399 bootloader binary blobs"
DESCRIPTION = "Binary blobs for RK3399 boot process: idbloader, uboot.img, trust.bin"
LICENSE = "Proprietary"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Proprietary;md5=0557f9d92cf58f2ccdd50f62f8ac0b28"

inherit deploy

DEPENDS = " \
    u-boot-rockchip \
    arm-trusted-firmware-rk3399 \
    rk-binary-native \
    rkbin-tools-native \
"

# Ensure U-Boot is compiled before we try to use its mkimage
do_compile[depends] += "u-boot-rockchip:do_compile"
# Also depend on rk-binary-native for boot_merger and make.sh scripts
do_compile[depends] += "rk-binary-native:do_populate_sysroot"

# Prefer rk-binary-native's tools over rkbin-tools-native to avoid conflicts
PREFERRED_PROVIDER_trust_merger-native = "rk-binary-native"
PREFERRED_PROVIDER_loaderimage-native = "rk-binary-native"

S = "${WORKDIR}"

# Copy make.sh and related scripts from reference code
copy_make_scripts() {
    # Copy make.sh and scripts from reference code
    install -d ${S}/scripts
    if [ -f "${TOPDIR}/../参考代码/u-boot-rockchip-scripts/make.sh" ]; then
        cp -r ${TOPDIR}/../参考代码/u-boot-rockchip-scripts/make.sh ${S}/
        chmod +x ${S}/make.sh
    fi
    if [ -d "${TOPDIR}/../参考代码/u-boot-rockchip-scripts/scripts" ]; then
        cp -r ${TOPDIR}/../参考代码/u-boot-rockchip-scripts/scripts/* ${S}/scripts/ 2>/dev/null || true
    fi
    
    # Copy RKBOOT directory with INI files
    install -d ${S}/rkbin/RKBOOT
    if [ -d "${TOPDIR}/../参考代码/rkbin-scripts/RKBOOT" ]; then
        cp -r ${TOPDIR}/../参考代码/rkbin-scripts/RKBOOT/* ${S}/rkbin/RKBOOT/ 2>/dev/null || true
    fi
}

do_unpack[postfuncs] += "copy_make_scripts"

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
    
    # Try to create loader.bin using make.sh script (like meta-rockchip does)
    # This uses boot_merger with proper INI file, which should generate correct loader.bin
    if [ -f "${S}/make.sh" ] && [ -f "${S}/rkbin/RKBOOT/RK3399MINIALL.ini" ] && [ -f "${STAGING_BINDIR_NATIVE}/boot_merger" ]; then
        bbnote "Creating loader.bin using make.sh script (like meta-rockchip)"
        
        # Prepare environment for make.sh
        cd ${B}
        
        # Create rkbin directory structure that loader.sh expects
        mkdir -p ${B}/rkbin/tools
        mkdir -p ${B}/rkbin/bin/rk33
        
        # Link boot_merger to expected location (loader.sh expects ./tools/boot_merger)
        ln -sf ${STAGING_BINDIR_NATIVE}/boot_merger ${B}/rkbin/tools/boot_merger
        
        # Link actual blob files to expected locations
        # Find actual DDR and miniloader blobs
        ACTUAL_DDR_BLOB=$(find ${STAGING_DIR_NATIVE}${datadir}/rkbin -name "rk3399_ddr*.bin" 2>/dev/null | head -1)
        ACTUAL_MINILOADER_BLOB=$(find ${STAGING_DIR_NATIVE}${datadir}/rkbin -name "rk3399_miniloader*.bin" 2>/dev/null | head -1)
        
        if [ -f "${ACTUAL_DDR_BLOB}" ] && [ -f "${ACTUAL_MINILOADER_BLOB}" ]; then
            # Link to expected names in INI file (rk3399_ddr_800MHz_v1.26.bin and rk3399_miniloader_v1.26.bin)
            # The INI file expects specific filenames, so we create symlinks
            ln -sf ${ACTUAL_DDR_BLOB} ${B}/rkbin/bin/rk33/rk3399_ddr_800MHz_v1.26.bin
            ln -sf ${ACTUAL_MINILOADER_BLOB} ${B}/rkbin/bin/rk33/rk3399_miniloader_v1.26.bin
            
            # Also check for usbplug blob (may not exist, that's okay)
            # If not found, create a dummy file or skip that section
            ACTUAL_USBPLUG_BLOB=$(find ${STAGING_DIR_NATIVE}${datadir}/rkbin -name "*usbplug*.bin" 2>/dev/null | head -1)
            if [ -f "${ACTUAL_USBPLUG_BLOB}" ]; then
                ln -sf ${ACTUAL_USBPLUG_BLOB} ${B}/rkbin/bin/rk33/rk3399_usbplug_v1.26.bin
            else
                # Create a dummy usbplug file (some INI files require it)
                touch ${B}/rkbin/bin/rk33/rk3399_usbplug_v1.26.bin
                bbnote "usbplug blob not found, creating dummy file"
            fi
            
            # Copy INI file and update paths to be relative to rkbin
            cp ${S}/rkbin/RKBOOT/RK3399MINIALL.ini ${B}/RK3399MINIALL.ini
            
            # Copy INI file to rkbin directory (boot_merger expects it relative to rkbin)
            cp ${S}/rkbin/RKBOOT/RK3399MINIALL.ini ${B}/rkbin/RK3399MINIALL.ini
            
            # Try to use make.sh's loader.sh script directly
            if [ -f "${S}/scripts/loader.sh" ]; then
                # Run loader.sh from rkbin directory (it expects to be run from rkbin)
                cd ${B}/rkbin
                bash ${S}/scripts/loader.sh --ini ${B}/rkbin/RK3399MINIALL.ini 2>&1 || {
                    bbwarn "loader.sh failed, trying boot_merger directly"
                    # Fallback: use boot_merger directly (from rkbin directory)
                    ${STAGING_BINDIR_NATIVE}/boot_merger ${B}/rkbin/RK3399MINIALL.ini 2>&1 || {
                        bbwarn "boot_merger failed, using idbloader.bin as loader.bin"
                        cd ${B}
                        cp ${B}/idbloader.bin ${B}/loader.bin
                    }
                }
                cd ${B}
                
                # Check if loader.bin was generated
                if [ -f "${B}/rkbin/rk3399_loader_v1.26.126.bin" ]; then
                    mv ${B}/rkbin/rk3399_loader_v1.26.126.bin ${B}/loader.bin
                    bbnote "Successfully created loader.bin using boot_merger"
                elif [ -f "${B}/rkbin/loader.bin" ]; then
                    mv ${B}/rkbin/loader.bin ${B}/loader.bin
                    bbnote "Successfully created loader.bin using loader.sh"
                else
                    bbwarn "loader.bin not generated, using idbloader.bin"
                    cp ${B}/idbloader.bin ${B}/loader.bin
                fi
            else
                # Use boot_merger directly
                cd ${B}/rkbin
                ${STAGING_BINDIR_NATIVE}/boot_merger ${B}/rkbin/RK3399MINIALL.ini 2>&1 || {
                    bbwarn "boot_merger failed, using idbloader.bin as loader.bin"
                    cd ${B}
                    cp ${B}/idbloader.bin ${B}/loader.bin
                }
                cd ${B}
                
                # Check if loader.bin was generated
                if [ -f "${B}/rkbin/rk3399_loader_v1.26.126.bin" ]; then
                    mv ${B}/rkbin/rk3399_loader_v1.26.126.bin ${B}/loader.bin
                    bbnote "Successfully created loader.bin using boot_merger"
                else
                    bbwarn "loader.bin not generated, using idbloader.bin"
                    cp ${B}/idbloader.bin ${B}/loader.bin
                fi
            fi
        else
            bbwarn "DDR or miniloader blob not found, using idbloader.bin as loader.bin"
            cp ${B}/idbloader.bin ${B}/loader.bin
        fi
    else
        # Fallback: use idbloader.bin as loader.bin
        bbnote "make.sh, INI file, or boot_merger not found, using idbloader.bin as loader.bin"
        cp ${B}/idbloader.bin ${B}/loader.bin
    fi
    
    # Create uboot.img using loaderimage tool
    # Format: loaderimage --pack --uboot <u-boot.bin> uboot.img <offset>
    # Offset: 0x200000 (2097152 bytes = 4096 sectors)
    # Use loaderimage from rk-binary-native (not rkbin-tools-native to avoid conflicts)
    LOADERIMAGE_CMD="${STAGING_BINDIR_NATIVE}/loaderimage"
    if [ ! -f "${LOADERIMAGE_CMD}" ]; then
        # Fallback: try to find in work directory
        LOADERIMAGE_CMD=$(find ${TMPDIR}/work/x86_64-linux/rk-binary-native/*/build -name "loaderimage" 2>/dev/null | head -1)
    fi
    
    if [ -f "${LOADERIMAGE_CMD}" ]; then
        ${LOADERIMAGE_CMD} \
            --pack \
            --uboot ${UBOOT_BIN} \
            ${B}/uboot.img \
            0x200000
    else
        bbfatal "loaderimage not found"
    fi
    
    # Create trust.bin using trust_merger from rk-binary-native
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
    
    # Use trust_merger from rk-binary-native (not rkbin-tools-native to avoid conflicts)
    TRUST_MERGER_CMD="${STAGING_BINDIR_NATIVE}/trust_merger"
    if [ ! -f "${TRUST_MERGER_CMD}" ]; then
        # Fallback: try to find in work directory
        TRUST_MERGER_CMD=$(find ${TMPDIR}/work/x86_64-linux/rk-binary-native/*/build -name "trust_merger" 2>/dev/null | head -1)
    fi
    
    if [ -f "${TRUST_MERGER_CMD}" ]; then
        ${TRUST_MERGER_CMD} \
            --replace bl31.elf ${BL31_BLOB} \
            ${B}/trust.ini
    else
        bbfatal "trust_merger not found"
    fi
}

do_install() {
    install -d ${D}${datadir}/rk3399-blobs
    install -m 0644 ${B}/idbloader.bin ${D}${datadir}/rk3399-blobs/
    install -m 0644 ${B}/loader.bin ${D}${datadir}/rk3399-blobs/
    install -m 0644 ${B}/uboot.img ${D}${datadir}/rk3399-blobs/
    install -m 0644 ${B}/trust.bin ${D}${datadir}/rk3399-blobs/
}

do_deploy() {
    install -d ${DEPLOY_DIR_IMAGE}
    install -m 0644 ${B}/idbloader.bin ${DEPLOY_DIR_IMAGE}/
    install -m 0644 ${B}/loader.bin ${DEPLOY_DIR_IMAGE}/
    install -m 0644 ${B}/uboot.img ${DEPLOY_DIR_IMAGE}/
    install -m 0644 ${B}/trust.bin ${DEPLOY_DIR_IMAGE}/
}

addtask deploy before do_build after do_install

FILES:${PN} = "${datadir}/rk3399-blobs"

