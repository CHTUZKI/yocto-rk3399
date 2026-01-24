SUMMARY = "Python X Library"
DESCRIPTION = "python-xlib is a Python X client library providing access to X11 protocol"
HOMEPAGE = "https://github.com/python-xlib/python-xlib"
LICENSE = "LGPL-2.1-or-later"
LIC_FILES_CHKSUM = "file://LICENSE;md5=c0a6a33a89f4e88a2e50e6f85ec5a0bd"

SRC_URI[sha256sum] = "55af7906a2c75ce055f7f7a7b9a8fbb4ecd907c6e476d1124d75621f4e9edb7d"

inherit pypi setuptools3

PYPI_PACKAGE = "python-xlib"

RDEPENDS:${PN} = " \
    python3-six \
    python3-xml \
"

BBCLASSEXTEND = "native nativesdk"
