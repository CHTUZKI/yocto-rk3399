# Copyright (C) 2019, Fuzhou Rockchip Electronics Co., Ltd
# Released under the MIT license (see COPYING.MIT for the terms)
# Adapted from meta-rockchip

SUMMARY = "Rockchip binary tools"
DESCRIPTION = "Native tools for Rockchip firmware packaging: afptool, rkImageMaker, etc."

inherit local-git deploy native

LICENSE = "Proprietary"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/Proprietary;md5=0557f9d92cf58f2ccdd50f62f8ac0b28"

# Use the same source as meta-rockchip
SRC_URI = " \
	git://github.com/JeffyCN/mirrors.git;protocol=https;nobranch=1;branch=rkbin-2021_10_13;name=rkbin;destsuffix=sources/rkbin \
	git://github.com/JeffyCN/mirrors.git;protocol=https;branch=tools;name=tools;destsuffix=sources/tools \
"

SRCREV_rkbin = "c41b714cacd249e3ef69b2bbe774da5095eefd72"
SRCREV_tools = "1a32bc776af52494144fcef6641a73850cee628a"
SRCREV_FORMAT ?= "rkbin_tools"

S = "${UNPACKDIR}/sources"

INSANE_SKIP:${PN} = "already-stripped"
STRIP = "echo"

# The pre-built tools have different link loader, don't change them.
UNINATIVE_LOADER := ""

# No configuration needed for pre-built binaries
do_configure[noexec] = "1"

do_install () {
	install -d ${D}${bindir}

	# Check if sources directory exists, try both UNPACKDIR and WORKDIR
	SOURCES_DIR="${S}"
	if [ ! -d "${SOURCES_DIR}" ]; then
		# Try WORKDIR as fallback (UNPACKDIR usually equals WORKDIR)
		SOURCES_DIR="${WORKDIR}/sources"
		if [ ! -d "${SOURCES_DIR}" ]; then
			bbfatal "Cannot find sources directory. Expected: ${S} or ${WORKDIR}/sources. Check do_unpack task."
		fi
		bbnote "Using ${SOURCES_DIR} instead of ${S}"
	fi

	find ${SOURCES_DIR} -type d -name rk_sign_tool -exec rm -rf {} + 2>/dev/null || true

	TOOLS="boot_merger trust_merger firmwareMerger kernelimage loaderimage \
		mkkrnlimg resource_tool upgrade_tool afptool rkImageMaker"

	for tool in ${TOOLS}; do
		find ${SOURCES_DIR} -type f -name ${tool} -exec \
			install -v -m 0755 {} ${D}${bindir} \;
	done
}

