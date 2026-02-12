SUMMARY = "BWidget - High-level widget set for Tcl/Tk"
DESCRIPTION = "BWidget is a set of native Tk widgets using Tk 8.x built-in \
megawidget support. This includes new widgets like: ComboBox, NoteBook, \
Tree, ListBox, PagesManager, etc."
HOMEPAGE = "https://sourceforge.net/projects/tcllib/"
LICENSE = "CLOSED"
LIC_FILES_CHKSUM = "file://LICENSE.txt;md5=217cb59420bb59460ca5e3c8508fb361"

SRC_URI = "file://bwidget-1.9.16.tar.gz"
SRC_URI[sha256sum] = "2e4ff62090674df620d634cfff11846deefdb2867cdac5f2e7417e28a06ddffa"

S = "${WORKDIR}/bwidget"

DEPENDS = "tcl tk"
RDEPENDS:${PN} = "tcl tk"

do_configure() {
    :
}

do_compile() {
    :
}

do_install() {
    install -d ${D}${libdir}/tcltk/bwidget${PV}
    cp -r ${S}/* ${D}${libdir}/tcltk/bwidget${PV}/
    rm -rf ${D}${libdir}/tcltk/bwidget${PV}/demo
    # Create pkgIndex.tcl if not exists
    if [ ! -f ${D}${libdir}/tcltk/bwidget${PV}/pkgIndex.tcl ]; then
        echo "package ifneeded BWidget ${PV} [list source [file join \$dir bwidget.tcl]]" > ${D}${libdir}/tcltk/bwidget${PV}/pkgIndex.tcl
    fi

    ln -sf ../tcltk/bwidget${PV} ${D}${libdir}/bwidget${PV}

    install -d ${D}${libdir}/tcl8.6
    ln -sf ../tcltk/bwidget${PV} ${D}${libdir}/tcl8.6/bwidget${PV}
}

FILES:${PN} = "${libdir}/tcltk/bwidget${PV} ${libdir}/bwidget${PV} ${libdir}/tcl8.6/bwidget${PV}"

BBCLASSEXTEND = "native nativesdk"
