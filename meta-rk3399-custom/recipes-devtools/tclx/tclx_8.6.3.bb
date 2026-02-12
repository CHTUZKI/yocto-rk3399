SUMMARY = "Extended Tcl (TclX)"
DESCRIPTION = "Extended Tcl (TclX) is a set of extensions to the Tcl programming \
language. It provides many new commands such as file and process control, \
time and date handling, advanced list processing, and many more."
HOMEPAGE = "https://github.com/flightaware/tclx"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://license.terms;md5=d1b75cd3cd65de13adee2b067107a694"

SRC_URI = "git://github.com/flightaware/tclx.git;branch=master;protocol=https \
           file://tclx-cross-times.patch \
           "
SRCREV = "6320ab951ef78dd7c3aeedb6a5a8d2ae9f3e4728"
PV = "8.6.3+git${SRCPV}"

S = "${WORKDIR}/git"

DEPENDS = "tcl tcl-native"

inherit autotools

EXTRA_OECONF = "--with-tcl=${STAGING_LIBDIR} \
                --with-tclinclude=${STAGING_INCDIR}/tcl8.6 \
                --enable-shared \
                "

do_configure:prepend() {
    # TclX uses tclConfig.sh for configuration
    export TCL_LIBRARY=${STAGING_LIBDIR}
}

do_install:append() {
    # Ensure proper installation paths
    install -d ${D}${libdir}/tcltk
}

FILES:${PN} = "${bindir} ${bindir}/* ${libdir}/*.so* ${libdir}/tclx* ${libdir}/tcltk ${libdir}/tcltk/*"
FILES:${PN}-dev = "${includedir} ${libdir}/*.a"

RDEPENDS:${PN} = "tcl"

BBCLASSEXTEND = "native nativesdk"
