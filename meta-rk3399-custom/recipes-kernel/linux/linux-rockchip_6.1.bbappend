# RK3399 Linux Kernel Recipe Append
# 应用修复配置到原始文件

FILESEXTRAPATHS:prepend := "${THISDIR}/linux-rockchip:"

# 内核配置已直接整合到defconfig中
# 设备树修复已直接整合到rk3399-firefly-core.dtsi中

# I2C调试支持已通过设备树配置实现，无需PACKAGECONFIG

# 增强内核配置
KERNEL_EXTRA_FEATURES += " \
    features/regulator/regulator.scc \
    features/i2c/i2c-debug.scc \
    features/mmc/mmc-stability.scc \
"

# 确保关键驱动被包含
KERNEL_MODULE_AUTOLOAD += " \
    rk808-regulator \
    fan53555-regulator \
    dwmmc_rockchip \
    sdhci_arasan \
    rk_gmac_dwmac \
"

# 添加构建时验证
do_compile:prepend() {
    echo "验证I2C配置..."
    grep -q "clock-frequency = <100000>" ${S}/arch/arm64/boot/dts/rockchip/rk3399-firefly-core.dtsi || {
        bbwarn "I2C频率配置可能未正确应用"
    }
    
    echo "验证调节器配置..."
    grep -q "vcc_1v8: DCDC_REG4" ${S}/arch/arm64/boot/dts/rockchip/rk3399-firefly-core.dtsi || {
        bbwarn "DCDC_REG4配置可能未正确应用"
    }
}
