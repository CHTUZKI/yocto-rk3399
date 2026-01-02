# RK3399 Linux Kernel Recipe Append

FILESEXTRAPATHS:prepend := "${THISDIR}/linux-rockchip:"

# 内核配置已直接整合到defconfig中
# 设备树使用 rk3399-evb.dts（评估板配置，来自内核源码）
# 注意：Armbian 内核不使用 Yocto 的 kernel features 系统

# 确保关键驱动模块自动加载（仅保留 rk3399-evb.dts 实际需要的）
KERNEL_MODULE_AUTOLOAD += " \
    rk808-regulator \
    rk_gmac_dwmac \
"
