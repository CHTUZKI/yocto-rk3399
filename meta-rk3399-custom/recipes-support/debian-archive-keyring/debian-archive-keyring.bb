SUMMARY = "Debian archive GnuPG keys for apt verification"
DESCRIPTION = "Installs Debian Bookworm archive keyring into /etc/apt/trusted.gpg.d/ so apt-get update can verify repository signatures without manual apt-key."
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

# Debian bookworm keyring (all arch)
# Update version and sha256 when upgrading: https://packages.debian.org/bookworm/debian-archive-keyring
SRC_URI = "http://ftp.debian.org/debian/pool/main/d/debian-archive-keyring/debian-archive-keyring_2023.3+deb12u2_all.deb;downloadfilename=debian-archive-keyring.deb;unpack=false"
SRC_URI[sha256sum] = "f699e2f88dca05212f2a452b58475f2993cb6993dfbafb1d0205a3291eb8b4b8"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${sysconfdir}/apt/trusted.gpg.d
    cd ${WORKDIR}
    ar x ${DL_DIR}/debian-archive-keyring.deb
    tar xf data.tar* -C ${D}
    if [ -f ${D}/usr/share/keyrings/debian-archive-keyring.gpg ]; then
        install -m 0644 ${D}/usr/share/keyrings/debian-archive-keyring.gpg ${D}${sysconfdir}/apt/trusted.gpg.d/debian-bookworm-archive.gpg
    fi
    rm -rf ${D}/usr
}

FILES:${PN} = "${sysconfdir}/apt/trusted.gpg.d/*.gpg ${sysconfdir}/apt/trusted.gpg.d/*.asc"

INSANE_SKIP:${PN} = "already-stripped ldflags file-rdeps"
