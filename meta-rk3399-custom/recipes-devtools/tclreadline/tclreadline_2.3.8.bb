SUMMARY = "GNU Readline extension for Tcl/Tk"
DESCRIPTION = "tclreadline adds readline capability to tcl shell, providing \
command-line editing and history."
HOMEPAGE = "https://github.com/flightaware/tclreadline"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://COPYING;md5=0ed764ffba5b33604dd2f7c917a2a087"

SRC_URI = "git://github.com/flightaware/tclreadline.git;branch=master;protocol=https"
SRCREV = "${AUTOREV}"
PV = "2.3.8+git${SRCPV}"

S = "${WORKDIR}/git"

DEPENDS = "tcl tk readline ncurses"

inherit autotools

EXTRA_OECONF = "--with-tcl=${STAGING_LIBDIR} \
                --with-tk=${STAGING_LIBDIR} \
                --with-tcl-includes=${STAGING_INCDIR}/tcl8.6 \
                --with-readline-includes=${STAGING_INCDIR} \
                --with-readline-library='-L${STAGING_LIBDIR} -lreadline' \
                "

FILES:${PN} = "${bindir} ${bindir}/* ${libdir}/libtclreadline-*.so ${libdir}/tclreadline*"
FILES:${PN}-dev = "${includedir} ${libdir}/libtclreadline.so"

RDEPENDS:${PN} = "tcl readline"

INSANE_SKIP:${PN}-dev += "dev-elf"

BBCLASSEXTEND = "native nativesdk"
