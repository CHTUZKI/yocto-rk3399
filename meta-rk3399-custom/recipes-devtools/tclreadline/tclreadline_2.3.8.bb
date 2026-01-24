SUMMARY = "GNU Readline extension for Tcl/Tk"
DESCRIPTION = "tclreadline adds readline capability to tcl shell, providing \
command-line editing and history."
HOMEPAGE = "https://github.com/flightaware/tclreadline"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://COPYING;md5=d6d35e98d9ea5abe40bbf47af564b5c8"

SRC_URI = "git://github.com/flightaware/tclreadline.git;branch=master;protocol=https"
SRCREV = "v${PV}"

S = "${WORKDIR}/git"

DEPENDS = "tcl readline ncurses"

inherit autotools

EXTRA_OECONF = "--with-tcl=${STAGING_LIBDIR} \
                --with-readline=${STAGING_LIBDIR} \
                "

FILES:${PN} = "${libdir}/*.so* ${libdir}/tclreadline*"
FILES:${PN}-dev = "${includedir}"

RDEPENDS:${PN} = "tcl readline"

BBCLASSEXTEND = "native nativesdk"
