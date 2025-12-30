# Rockchip image class
# Generates update.img format for Rockchip devices using official tools
# This format can be recognized by RKDevTool.exe and upgrade_tool
# Based on meta-rockchip implementation

inherit image

export RK_ROOTDEV_UUID ?= "614e0000-0000-4b53-8000-1d28000054a9"
export RK_PARTITION_GROW ?= "1"
export RK_ROOTFS_TYPE ?= "ext4"

# Generate Rockchip parameter file from partition table
gen_rkparameter() {
	# Check for either loader.bin or idbloader.bin
	if [ ! -f "${DEPLOY_DIR_IMAGE}/loader.bin" ] && [ ! -f "${DEPLOY_DIR_IMAGE}/idbloader.bin" ]; then
		bbnote "Skip making Rockchip parameter (loader.bin/idbloader.bin not found)"
		return
	fi

	IMAGE="${IMGDEPLOYDIR}/${IMAGE_LINK_NAME}.wic"
	if [ ! -f "${IMAGE}" ]; then
		bbwarn "${IMAGE} not found."
		return
	fi

	cd "${IMGDEPLOYDIR}"

	OUT="${IMAGE_LINK_NAME}.parameter"
	ln -sf "${OUT}" parameter

	bbnote "Generating ${OUT}..."

	echo "# IMAGE_NAME: $(readlink ${IMAGE})" > "${OUT}"
	echo "FIRMWARE_VER: 1.0" >> "${OUT}"
	echo "TYPE: GPT" >> "${OUT}"
	echo -n "CMDLINE: mtdparts=rk29xxnand:" >> "${OUT}"
	
	# Add uboot and trust partitions explicitly (before GPT partitions)
	# These are at fixed offsets before the partition table
	# uboot.img at sector 16384 (0x4000), size 4MB (0x2000 sectors)
	# trust.bin at sector 24576 (0x6000), size 4MB (0x2000 sectors)
	# Note: miniloader needs these in parameter file to find uboot
	# uboot-env will be handled as a GPT partition, not fixed offset
	echo -n "0x00002000@0x0004000(uboot),0x00002000@0x0006000(trust)," >> "${OUT}"
	
	# Add GPT partitions from the image, but adjust partition starts to avoid overlap
	# Boot partition should start after trust (0x8000) to avoid overlap
	# Root partition should start after boot to avoid overlap
	# First pass: calculate boot end position
	BOOT_END=0
	sgdisk -p "${IMAGE}" | grep -E "^ +[0-9]" | while read line; do
		NAME=$(echo ${line} | cut -f 7 -d ' ')
		START=$(echo ${line} | cut -f 2 -d ' ')
		END=$(echo ${line} | cut -f 3 -d ' ')
		SIZE=$(expr ${END} - ${START} + 1)
		if [ "${NAME}" = "boot" ] && [ ${START} -lt 32768 ]; then
			# Boot should start at 0x8000 (32768 sectors) after trust
			NEW_START=32768
			NEW_END=$(expr ${NEW_START} + ${SIZE} - 1)
			echo ${NEW_END} > "${IMGDEPLOYDIR}/.boot_end"
		fi
	done
	
	# Read boot end position if it was calculated
	if [ -f "${IMGDEPLOYDIR}/.boot_end" ]; then
		BOOT_END=$(cat "${IMGDEPLOYDIR}/.boot_end")
		rm -f "${IMGDEPLOYDIR}/.boot_end"
	else
		# If boot wasn't adjusted, calculate from original
		sgdisk -p "${IMAGE}" | grep -E "^ +[0-9]" | while read line; do
			NAME=$(echo ${line} | cut -f 7 -d ' ')
			START=$(echo ${line} | cut -f 2 -d ' ')
			END=$(echo ${line} | cut -f 3 -d ' ')
			if [ "${NAME}" = "boot" ]; then
				echo ${END} > "${IMGDEPLOYDIR}/.boot_end_orig"
			fi
		done
		if [ -f "${IMGDEPLOYDIR}/.boot_end_orig" ]; then
			BOOT_END=$(cat "${IMGDEPLOYDIR}/.boot_end_orig")
			rm -f "${IMGDEPLOYDIR}/.boot_end_orig"
		fi
	fi
	
	# Second pass: output partitions with adjustments
	sgdisk -p "${IMAGE}" | grep -E "^ +[0-9]" | while read line; do
		NAME=$(echo ${line} | cut -f 7 -d ' ')
		START=$(echo ${line} | cut -f 2 -d ' ')
		END=$(echo ${line} | cut -f 3 -d ' ')
		SIZE=$(expr ${END} - ${START} + 1)
		# If boot partition starts before 0x8000, adjust it to start at 0x8000
		if [ "${NAME}" = "boot" ] && [ ${START} -lt 32768 ]; then
			# Boot should start at 0x8000 (32768 sectors) after trust
			START=32768
			END=$(expr ${START} + ${SIZE} - 1)
		fi
		# If root partition starts before boot ends, adjust it to start after boot
		if [ "${NAME}" = "root" ] && [ ${BOOT_END} -gt 0 ] && [ ${START} -le ${BOOT_END} ]; then
			# Root should start after boot ends (BOOT_END is inclusive, so use BOOT_END+1)
			START=$(expr ${BOOT_END} + 1)
			# Keep the same size (or use grow)
		fi
		printf "0x%08x@0x%08x(%s)," ${SIZE} ${START} ${NAME} >> "${OUT}"
	done
	echo >> "${OUT}"

	# Disable grow for uboot-env partition to avoid Rockchip tool conflicts
	# if [ "${RK_PARTITION_GROW}" = "1" ]; then
	# 	sed -i 's/[^,]*\(@[^,]*\)),$/-\1:grow)/' "${OUT}"
	# fi

	echo "uuid: rootfs=${RK_ROOTDEV_UUID}" >> "${OUT}"
}

# Generate Rockchip update.img using official tools
# Note: gen_rkparameter must run before gen_rkupdateimg
do_image[depends] += "rk-binary-native:do_populate_sysroot"
gen_rkupdateimg() {
	# Check for loader.bin or idbloader.bin
	# For RKDevTool compatibility, we need to use idbloader.bin directly
	# as loader.bin (they are the same format for RK3399)
	LOADER_BIN=""
	if [ -f "${DEPLOY_DIR_IMAGE}/loader.bin" ] && [ ! -L "${DEPLOY_DIR_IMAGE}/loader.bin" ]; then
		# Use existing loader.bin if it's not a symlink
		LOADER_BIN="loader.bin"
	elif [ -f "${DEPLOY_DIR_IMAGE}/idbloader.bin" ]; then
		# Use idbloader.bin directly (copy, not symlink, for rkImageMaker)
		LOADER_BIN="idbloader.bin"
		# Copy idbloader.bin to loader.bin (rkImageMaker needs loader.bin)
		# Only copy if loader.bin doesn't exist or is a symlink
		if [ ! -f "${DEPLOY_DIR_IMAGE}/loader.bin" ] || [ -L "${DEPLOY_DIR_IMAGE}/loader.bin" ]; then
			cp -f "${DEPLOY_DIR_IMAGE}/idbloader.bin" "${DEPLOY_DIR_IMAGE}/loader.bin" 2>/dev/null || {
				# If copy fails (same file), just use idbloader.bin directly
				bbnote "Using idbloader.bin directly as loader.bin"
			}
		fi
	else
		bbnote "Skip packing Rockchip update image (loader.bin/idbloader.bin not found)"
		return
	fi

	IMAGE="${IMGDEPLOYDIR}/${IMAGE_LINK_NAME}.wic"
	if [ ! -f "${IMAGE}" ]; then
		bbwarn "${IMAGE} not found."
		return
	fi

	cd "${IMGDEPLOYDIR}"

	# Create rootfs.img symlink if it doesn't exist (must be done before package-file generation)
	if [ ! -f "rootfs.img" ] && [ ! -L "rootfs.img" ]; then
		if [ -f "${IMAGE_LINK_NAME}.${RK_ROOTFS_TYPE}" ]; then
			ln -sf "${IMAGE_LINK_NAME}.${RK_ROOTFS_TYPE}" rootfs.img
			bbnote "Created rootfs.img symlink to ${IMAGE_LINK_NAME}.${RK_ROOTFS_TYPE}"
		elif [ -f "${DEPLOY_DIR_IMAGE}/${IMAGE_LINK_NAME}.${RK_ROOTFS_TYPE}" ]; then
			rel_path=$(realpath --relative-to="${IMGDEPLOYDIR}" "${DEPLOY_DIR_IMAGE}/${IMAGE_LINK_NAME}.${RK_ROOTFS_TYPE}")
			ln -sf "${rel_path}" rootfs.img
			bbnote "Created rootfs.img symlink to ${rel_path}"
		fi
	fi

	# Create trust.img symlink if it doesn't exist (must be done before package-file generation)
	if [ ! -f "trust.img" ] && [ ! -L "trust.img" ]; then
		if [ -f "${DEPLOY_DIR_IMAGE}/trust.bin" ]; then
			rel_path=$(realpath --relative-to="${IMGDEPLOYDIR}" "${DEPLOY_DIR_IMAGE}/trust.bin")
			ln -sf "${rel_path}" trust.img
			bbnote "Created trust.img symlink to ${rel_path}"
		fi
	fi

	RK_IMAGES="loader.bin uboot.env uboot.img trust.img boot.img rootfs.img"

	# Create temporary symlinks, because the tool would crash with abs pathes
	# Use relative paths to avoid sstate issues
	for img in ${RK_IMAGES}; do
		f="${DEPLOY_DIR_IMAGE}/${img}"
		# Also check for trust.bin if trust.img doesn't exist
		if [ "${img}" = "trust.img" ] && [ ! -f "${f}" ]; then
			f="${DEPLOY_DIR_IMAGE}/trust.bin"
		fi
		# For rootfs.img, check in IMGDEPLOYDIR first
		if [ "${img}" = "rootfs.img" ] && [ ! -f "${f}" ] && [ -f "${IMGDEPLOYDIR}/rootfs.img" ]; then
			f="${IMGDEPLOYDIR}/rootfs.img"
		fi
		if [ -f "${f}" ] || [ -L "${f}" ]; then
			# Use relative path for symlink
			rel_path=$(realpath --relative-to="${IMGDEPLOYDIR}" "${f}")
			ln -sf "${rel_path}" "${img}"
			bbnote "Linked ${img} -> ${rel_path}"
		else
			bbwarn "Missing image file: ${img} (checked ${f})"
		fi
	done

	OUT="${IMAGE_LINK_NAME}.package-file"
	ln -sf "${OUT}" package-file

	bbnote "Generating ${OUT}..."

	echo "# IMAGE_NAME: $(readlink ${IMAGE})" > "${OUT}"
	echo "package-file package-file" >> "${OUT}"
	echo "bootloader loader.bin" >> "${OUT}"
	echo "parameter parameter" >> "${OUT}"
	
	# Add uboot and trust explicitly (they are NOT in parameter file)
	# They are at fixed offsets (0x4000 and 0x6000) before the partition table
	if [ -f "uboot.img" ] || [ -L "uboot.img" ]; then
		echo "uboot uboot.img" >> "${OUT}"
		bbnote "Added uboot uboot.img to package-file"
	fi
	if [ -f "trust.img" ] || [ -f "trust.bin" ] || [ -L "trust.img" ]; then
		if [ -f "trust.img" ] || [ -L "trust.img" ]; then
			echo "trust trust.img" >> "${OUT}"
		else
			echo "trust trust.bin" >> "${OUT}"
		fi
		bbnote "Added trust partition to package-file"
	fi
	
	# Parse parameter file for GPT partitions only
	grep -o "([^)^:]*" parameter | tr -d "(" | while read NAME; do
		case "${NAME}" in
			uboot-env) IMAGE="uboot.env" ;;
			backup) echo "backup RESERVED" >> "${OUT}"; continue ;;
			system|system_[ab]|root) IMAGE="rootfs.img" ;;
			*_a) IMAGE="${NAME%_a}.img" ;;
			*_b) IMAGE="${NAME%_b}.img" ;;
			*) IMAGE="${NAME}.img" ;;
		esac

		[ ! -r "${IMAGE}" ] || echo "${NAME} ${IMAGE}" >> "${OUT}"
	done

	# Verify all required files exist before packing
	bbnote "Verifying files before packing..."
	for img in loader.bin uboot.img trust.img rootfs.img; do
		if [ ! -f "${img}" ] && [ ! -L "${img}" ]; then
			bbfatal "Required file ${img} not found in ${IMGDEPLOYDIR}"
		fi
	done

	PSEUDO_DISABLED=1
	bbnote "Running afptool to pack firmware..."
	${STAGING_BINDIR_NATIVE}/afptool -pack ./ update.raw.img || {
		bbfatal "afptool failed. Check if all files in package-file exist."
	}
	
	bbnote "Running rkImageMaker to create update.img..."
	${STAGING_BINDIR_NATIVE}/rkImageMaker -RK$(hexdump -s 21 -n 4 -e '4/1 "%c"' loader.bin | rev) \
		loader.bin update.raw.img "${IMAGE_LINK_NAME}.update.img" \
		-os_type:androidos || {
		bbfatal "rkImageMaker failed. Check loader.bin and update.raw.img."
	}
	ln -sf "${IMAGE_LINK_NAME}.update.img" update.img

	rm -rf ${RK_IMAGES} update.raw.img
}

# Generate parameter first, then update.img
IMAGE_POSTPROCESS_COMMAND:append = " gen_rkparameter; gen_rkupdateimg;"
