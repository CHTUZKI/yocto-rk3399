# Common configuration for RK3399 machines

# Bootloader
PREFERRED_PROVIDER_virtual/bootloader = "u-boot-rockchip"
UBOOT_SUFFIX = "img"
UBOOT_BINARY = "u-boot.itb"

# Kernel
PREFERRED_PROVIDER_virtual/kernel = "linux-mainline"
PREFERRED_VERSION_linux-mainline = "6.1.163"

# Machine architecture
TARGET_ARCH = "aarch64"
TUNE_FEATURES = "aarch64"

# Boot device
BOOT_DEVICE = "mmc"

# Image generation
IMAGE_FSTYPES += "wic wic.bmap"
WKS_FILE = "rk3399-sdimage.wks.in"

# Rockchip specific
RK3399_SOC = "rk3399"
RK3399_DDR_BLOB = "rk33/rk3399_ddr_933MHz_v1.25.bin"
RK3399_MINILOADER_BLOB = "rk33/rk3399_miniloader_v1.26.bin"
RK3399_BL31_BLOB = "rk33/rk3399_bl31_v1.35.elf"

# U-Boot environment
UBOOT_ENV_SIZE = "0x20000"

