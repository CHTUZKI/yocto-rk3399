#!/bin/bash
# 下载 cross-localedef-native 所需的源码

set -e

DOWNLOAD_DIR="/home/xuning/yocto-rk3399/build/downloads"
GLIBC_REV="4e50046821f05ada5f14c76803845125ddb3ed7d"
LOCALEDEF_REV="794da69788cbf9bf57b59a852f9f11307663fa87"

mkdir -p "${DOWNLOAD_DIR}"
cd "${DOWNLOAD_DIR}"

echo "=== 下载 glibc 源码 ==="
if [ ! -d "glibc-git" ]; then
    echo "正在克隆 glibc 仓库..."
    git clone --depth=1 --branch=release/2.35/master https://sourceware.org/git/glibc.git glibc-git
    cd glibc-git
    git checkout ${GLIBC_REV}
    cd ..
    echo "glibc 下载完成"
else
    echo "glibc-git 已存在，跳过下载"
    cd glibc-git
    git fetch --depth=1 origin release/2.35/master
    git checkout ${GLIBC_REV} 2>/dev/null || echo "已在正确的 commit"
    cd ..
fi

echo ""
echo "=== 下载 localedef 源码 ==="
if [ ! -d "localedef-git" ]; then
    echo "正在克隆 localedef 仓库..."
    # 不使用 --depth=1，因为需要特定的 commit
    git clone https://github.com/kraj/localedef.git localedef-git
    cd localedef-git
    git checkout ${LOCALEDEF_REV}
    cd ..
    echo "localedef 下载完成"
else
    echo "localedef-git 已存在，检查 commit..."
    cd localedef-git
    # 如果是浅克隆，先取消浅克隆限制
    if [ -f .git/shallow ]; then
        echo "取消浅克隆限制以获取完整历史..."
        git fetch --unshallow 2>/dev/null || git fetch origin
    else
        git fetch origin
    fi
    git checkout ${LOCALEDEF_REV} 2>/dev/null || (echo "获取指定 commit..." && git fetch origin ${LOCALEDEF_REV} && git checkout ${LOCALEDEF_REV})
    cd ..
fi

echo ""
echo "=== 下载完成 ==="
echo "glibc-git: $(du -sh glibc-git | cut -f1)"
echo "localedef-git: $(du -sh localedef-git | cut -f1)"
echo ""
echo "BitBake 会自动识别这些已下载的源码，加速构建过程"

