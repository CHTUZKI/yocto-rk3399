SUMMARY = "GTK+ source code editing widget"
DESCRIPTION = "GtkSourceView is a GNOME library that extends GtkTextView, the \
standard GTK+ widget for multiline text editing. GtkSourceView adds support \
for syntax highlighting, undo/redo, file loading and saving, search and replace."
HOMEPAGE = "https://wiki.gnome.org/Projects/GtkSourceView"
LICENSE = "LGPL-2.1-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=fbc093901857fcd118f065f900982c24"

DEPENDS = "gtk+3 libxml2 glib-2.0 pango cairo"

inherit gnomebase gettext gobject-introspection meson pkgconfig

SRC_URI = "https://download.gnome.org/sources/gtksourceview/4.8/gtksourceview-${PV}.tar.xz"
SRC_URI[sha256sum] = "7ec9d18fb283d1f84a3a3eff3b7a72b09a10c9c006597b3fbabbb5958420a3d3"

S = "${WORKDIR}/gtksourceview-${PV}"

EXTRA_OEMESON = " \
    -Dinstall_tests=false \
    -Dgtk_doc=false \
"

FILES:${PN} += " \
    ${datadir}/gtksourceview-4 \
"

BBCLASSEXTEND = "native nativesdk"
