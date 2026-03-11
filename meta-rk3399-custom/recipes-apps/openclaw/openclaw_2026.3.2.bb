SUMMARY = "OpenClaw Personal AI Assistant (CLI gateway)"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=2bc038cb3af1b762cde7b88a5dcecbd9"

SRC_URI = "https://registry.npmjs.org/openclaw/-/openclaw-${PV}.tgz"
SRC_URI[sha256sum] = "3ec99c93003725c3afb3d8c3236f7fb7054647d151dc27b9e0256e00372f31b7"

S = "${WORKDIR}/package"
PKGD = "${WORKDIR}/pkgdest"

SSTATE_SKIP_CREATION = "1"

python __anonymous() {
    d.delVarFlag('do_install', 'fakeroot')
    d.delVarFlag('do_package', 'fakeroot')
}

RDEPENDS:${PN} = "nodejs ca-certificates"

do_install() {
    install -d ${D}${libdir}/openclaw
    cp -R --no-dereference --preserve=mode,timestamps ${S}/* ${D}${libdir}/openclaw/

    install -d ${D}${bindir}
    cat > ${D}${bindir}/openclaw << 'EOF'
#!/bin/sh
exec node "@@LIBDIR@@/openclaw/openclaw.mjs" "$@"
EOF
    sed -i "s|@@LIBDIR@@|${libdir}|g" ${D}${bindir}/openclaw
    chmod 0755 ${D}${bindir}/openclaw
}

FILES:${PN} += "${libdir}/openclaw"
