SUMMARY = "BWidget - High-level widget set for Tcl/Tk"
DESCRIPTION = "BWidget is a set of native Tk widgets using Tk 8.x built-in \
megawidget support. This includes new widgets like: ComboBox, NoteBook, \
Tree, ListBox, PagesManager, etc."
HOMEPAGE = "https://sourceforge.net/projects/tcllib/"
LICENSE = "Tcl"
LIC_FILES_CHKSUM = "file://LICENSE.txt;md5=fc3c33c5602cb7e66d08f6a41d2869eb"

SRC_URI = "${SOURCEFORGE_MIRROR}/tcllib/bwidget-${PV}.tar.gz"
SRC_URI[sha256sum] = "bwidget-${PV}.tar.gz"

# Fallback to GitHub mirror if SourceForge is unavailable
SRC_URI = "https://github.com/tcltk/bwidget/archive/refs/tags/bwidget-${PV}.tar.gz"
SRC_URI[sha256sum] = "skip"

S = "${WORKDIR}/bwidget-bwidget-${PV}"

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
    # Create pkgIndex.tcl if not exists
    if [ ! -f ${D}${libdir}/tcltk/bwidget${PV}/pkgIndex.tcl ]; then
        echo "package ifneeded BWidget ${PV} [list source [file join \$dir bwidget.tcl]]" > ${D}${libdir}/tcltk/bwidget${PV}/pkgIndex.tcl
    fi
}

FILES:${PN} = "${libdir}/tcltk/bwidget${PV}"

BBCLASSEXTEND = "native nativesdk"
