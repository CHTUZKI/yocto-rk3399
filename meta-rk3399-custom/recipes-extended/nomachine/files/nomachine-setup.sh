#!/bin/bash
# NoMachine首次启动配置脚本
# 在首次启动时自动配置NoMachine
# 支持systemd和SysV init系统

SETUP_FLAG="/var/lib/nomachine/.setup-complete"

# 如果已经配置过，直接退出
if [ -f "$SETUP_FLAG" ]; then
    exit 0
fi

# 创建标志文件目录
mkdir -p "$(dirname "$SETUP_FLAG")"

# 创建 /run/systemd 目录（NoMachine需要，即使使用SysV init）
mkdir -p /run/systemd || true

# 检查NoMachine是否已配置
if [ ! -f /usr/NX/etc/server.cfg ] || [ ! -f /etc/init.d/nxserver ]; then
    echo "Configuring NoMachine on first boot..."
    
    # 运行NoMachine安装配置
    /usr/NX/nxserver --install debian >/var/log/nomachine-setup.log 2>&1
    
    if [ $? -eq 0 ]; then
        echo "NoMachine configuration completed successfully"
    else
        echo "Warning: NoMachine configuration had some errors, check /var/log/nomachine-setup.log"
    fi
fi

# 配置开机启动
if [ ! -f /etc/init.d/nxserver ]; then
    echo "Warning: NoMachine init script not found"
else
    # 确保开机启动已配置（SysV init）
    if [ ! -L /etc/rc2.d/S99nxserver ] && [ ! -L /etc/rc3.d/S99nxserver ]; then
        update-rc.d nxserver defaults >/dev/null 2>&1 || true
    fi
fi

# 标记配置完成
touch "$SETUP_FLAG"

# 启动NoMachine服务
if [ -f /etc/init.d/nxserver ]; then
    /etc/init.d/nxserver start >/dev/null 2>&1 || true
fi

exit 0

