注意！！！
我使用的镜像是update.img，使用RKDevTool.exe工具烧录到emmc


当前使用U-Boot直接启动方式，不依赖boot.scr脚本

U-Boot启动 → 执行固定bootcmd → 直接从root分区加载内核Image和设备树DTB → 启动内核

详细启动流程：
- U-Boot从mmcblk0p4分区(/dev/mmcblk0p4)加载内核到0x00280000
- U-Boot从同一分区加载设备树到0x03000000  
- 设置bootargs参数后直接启动内核

内核文件：/boot/Image-6.1.115
设备树：/boot/rk3399-firefly-aio.dtb