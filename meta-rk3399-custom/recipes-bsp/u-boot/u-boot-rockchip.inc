# Common includes for Rockchip U-Boot

inherit deploy

DEPENDS += "dtc-native bc-native bison-native flex-native python3-setuptools-native gnutls-native python3-pyelftools-native"

# Rockchip tools
DEPENDS += "rkbin-tools-native"

# ATF dependency
DEPENDS += "arm-trusted-firmware-rk3399"

# Build tools
EXTRA_OEMAKE += " \
    CROSS_COMPILE=${TARGET_PREFIX} \
    ARCH=arm \
"

# Rockchip specific build flags
# Note: BL31 path will be resolved at build time
EXTRA_OEMAKE += " \
    BL31=${DEPLOY_DIR_IMAGE}/bl31.elf \
"

# Output directory
UBOOT_OUTPUT_DIR = "${B}"

