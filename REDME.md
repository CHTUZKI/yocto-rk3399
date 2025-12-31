注意！！！
我使用的镜像是update.img，使用RKDevTool.exe工具烧录到emmc


所有启动逻辑都在 boot.scr 中

U-Boot启动 → 执行固定bootcmd → 从root分区加载/boot/boot.scr → 执行boot.scr → 加载kernel/DTB → 启动内核