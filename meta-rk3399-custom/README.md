# Yocto Layer for RK3399

This layer provides support for building a complete Linux system for Rockchip RK3399 SoC.

## Features

- U-Boot bootloader with Rockchip-specific patches
- Linux kernel from Armbian (rk-6.1-rkr5.1 branch)
- Rockchip binary blobs (DDR init, miniloader, ATF)
- Proper partition layout for eMMC/SD card
- Boot script support

## Dependencies

This layer depends on:

```
URI: https://git.yoctoproject.org/git/poky
branch: kirkstone (or newer)
```

```
URI: https://git.openembedded.org/meta-openembedded
branch: kirkstone (or newer)
```

```
URI: https://github.com/meta-rockchip/meta-rockchip
branch: master
```

## Setup

1. Add this layer to your `bblayers.conf`:

```bash
bitbake-layers add-layer /path/to/yocto-rk3399
```

2. Set the machine in `local.conf`:

```bash
MACHINE = "rk3399-firefly"
```

3. Build the image:

```bash
bitbake rk3399-image
```

## Machine Configuration

- `rk3399-firefly`: Firefly RK3399 board
- `rk3399-generic`: Generic RK3399 board

## Image Types

- `rk3399-image`: Full system image with Armbian kernel (rk-6.1-rkr5.1)

## Partition Layout

The image uses the following partition layout (compatible with Armbian):

- Sector 64 (0x40): idbloader.bin
- Sector 16384 (0x4000): uboot.img  
- Sector 24576 (0x6000): trust.bin
- From 16MB: Boot partition (FAT32)
- From 256MB: Root filesystem (ext4)

## Boot Process

1. Boot ROM loads idbloader from sector 64
2. idbloader loads miniloader
3. Miniloader loads U-Boot from sector 16384
4. U-Boot loads kernel and rootfs from partitions

