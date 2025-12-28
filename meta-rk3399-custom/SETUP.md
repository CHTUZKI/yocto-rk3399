# Yocto RK3399 Setup Guide

## Prerequisites

1. **Yocto Project** (Poky)
   ```bash
   git clone -b kirkstone https://git.yoctoproject.org/git/poky
   cd poky
   ```

2. **OpenEmbedded Meta Layer**
   ```bash
   git clone -b kirkstone https://git.openembedded.org/meta-openembedded
   ```

3. **Meta-Rockchip** (optional, but recommended)
   ```bash
   git clone https://github.com/meta-rockchip/meta-rockchip
   ```

## Setup Steps

### 1. Initialize Build Environment

```bash
source poky/oe-init-build-env build-rk3399
```

### 2. Add Layers

Edit `conf/bblayers.conf` and add:

```bash
bitbake-layers add-layer ../meta-openembedded/meta-oe
bitbake-layers add-layer ../meta-openembedded/meta-python
bitbake-layers add-layer ../meta-openembedded/meta-networking
bitbake-layers add-layer ../meta-rockchip  # if using
bitbake-layers add-layer /path/to/yocto-rk3399
```

### 3. Configure Machine

Edit `conf/local.conf`:

```bash
MACHINE = "rk3399-firefly"
```

### 4. Download RK3399 Binary Blobs

The RK3399 requires proprietary binary blobs. You need to:

1. Download rkbin from Rockchip:
   ```bash
   git clone https://github.com/rockchip-linux/rkbin.git
   ```

2. Copy blobs to a location accessible by the recipe, or modify `rkbin-tools-native.bb` to point to your rkbin directory.

### 5. Build Image

```bash
# Build RT image
bitbake rk3399-rt-image

# Or build base image
bitbake rk3399-base-image
```

## Output

The built image will be in:
```
tmp/deploy/images/rk3399-firefly/rk3399-rt-image-rk3399-firefly.wic
```

## Flashing to SD Card

```bash
# Using bmaptool (recommended)
bmaptool copy tmp/deploy/images/rk3399-firefly/rk3399-rt-image-rk3399-firefly.wic /dev/sdX

# Or using dd
dd if=tmp/deploy/images/rk3399-firefly/rk3399-rt-image-rk3399-firefly.wic of=/dev/sdX bs=4M status=progress
```

## Troubleshooting

### Missing Binary Blobs

If build fails due to missing blobs:
- Ensure rkbin is cloned and accessible
- Check `rkbin-tools-native.bb` paths
- Verify blob files exist in rkbin/rk33/

### U-Boot Build Errors

- Check that ATF (BL31) is built first
- Verify U-Boot defconfig exists
- Check patch compatibility with U-Boot version

### Kernel RT Patch Issues

- Verify kernel version matches RT patch version
- Check patch download URLs are accessible
- Review kernel config for conflicts

## Customization

### Change Kernel Version

Edit `recipes-kernel/linux/linux-rockchip-rt_6.12.bb`:
- Change `LINUX_VERSION`
- Update `KBRANCH`
- Update RT patch URL

### Add Custom Packages

Edit `recipes-core/images/rk3399-rt-image.bb`:
- Add packages to `IMAGE_INSTALL`

### Modify Partition Layout

Edit `wic/rk3399-sdimage.wks.in`:
- Adjust partition sizes
- Add/remove partitions

