# RK3399 Linux Kernel Recipe Append

FILESEXTRAPATHS:prepend := "${THISDIR}/linux-rockchip:"

# 内核配置已直接整合到defconfig中
# 设备树使用 rk3399-evb.dts（评估板配置，来自内核源码）
# 注意：Armbian 内核不使用 Yocto 的 kernel features 系统

SRC_URI += "file://preempt-rt.cfg \
            file://sysvipc.cfg \
           "

# 在配置阶段应用 PREEMPT_RT 和 SYSV IPC 配置片段
do_configure:append() {
    # 如果系统需要实时支持，则应用 PREEMPT_RT 配置
    if [ -f "${WORKDIR}/preempt-rt.cfg" ]; then
        cat ${WORKDIR}/preempt-rt.cfg >> ${B}/.config
    fi

    # 启用 LinuxCNC uspace 所需的 System V IPC 支持
    if [ -f "${WORKDIR}/sysvipc.cfg" ]; then
        cat ${WORKDIR}/sysvipc.cfg >> ${B}/.config
    fi

    # 重新运行 olddefconfig 以解决配置依赖
    oe_runmake -C ${S} O=${B} olddefconfig
}

# 确保关键驱动模块自动加载（仅保留 rk3399-evb.dts 实际需要的）
KERNEL_MODULE_AUTOLOAD += " \
    rk808-regulator \
    rk_gmac_dwmac \
"

# LinuxCNC 需要的额外内核模块
KERNEL_MODULE_AUTOLOAD += " \
    spidev \
    i2c-dev \
"
