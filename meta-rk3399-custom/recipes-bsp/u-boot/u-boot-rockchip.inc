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

# Ensure bl31.elf is deployed before U-Boot compilation
# This is critical when cleansstate is used, as it only cleans U-Boot's state
# but not the dependency's state, and bl31.elf might not be in DEPLOY_DIR_IMAGE
do_compile[depends] += "arm-trusted-firmware-rk3399:do_deploy"

# Output directory
UBOOT_OUTPUT_DIR = "${B}"

