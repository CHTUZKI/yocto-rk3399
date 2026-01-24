SUMMARY = "Extended Tcl (TclX)"
DESCRIPTION = "Extended Tcl (TclX) is a set of extensions to the Tcl programming \
language. It provides many new commands such as file and process control, \
time and date handling, advanced list processing, and many more."
HOMEPAGE = "https://github.com/flightaware/tclx"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://license.terms;md5=8c3c8f5a4fd9e4eb5d86f1bc917b8fab"

SRC_URI = "git://github.com/flightaware/tclx.git;branch=main;protocol=https"
SRCREV = "v${PV}"

S = "${WORKDIR}/git"

DEPENDS = "tcl tcl-native"

inherit autotools

EXTRA_OECONF = "--with-tcl=${STAGING_LIBDIR} \
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

FILES:${PN} = "${libdir}/*.so* ${libdir}/tclx* ${libdir}/tcltk/*"
FILES:${PN}-dev = "${includedir} ${libdir}/*.a"

RDEPENDS:${PN} = "tcl"

BBCLASSEXTEND = "native nativesdk"
