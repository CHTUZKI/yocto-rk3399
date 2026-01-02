# RK3399 Linux Kernel Recipe Append

FILESEXTRAPATHS:prepend := "${THISDIR}/linux-rockchip:"

# 内核配置已直接整合到defconfig中
# 设备树使用标准的 rk3399-firefly.dts（来自内核源码）
# 注意：Armbian 内核不使用 Yocto 的 kernel features 系统

# 确保关键驱动模块自动加载
KERNEL_MODULE_AUTOLOAD += " \
    rk808-regulator \
    fan53555-regulator \
    dwmmc_rockchip \
    sdhci_arasan \
    rk_gmac_dwmac \
"
