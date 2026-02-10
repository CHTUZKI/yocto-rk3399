SUMMARY = "Python X Library"
DESCRIPTION = "python-xlib is a Python X client library providing access to X11 protocol"
HOMEPAGE = "https://github.com/python-xlib/python-xlib"
LICENSE = "LGPL-2.1-or-later"
LIC_FILES_CHKSUM = "file://LICENSE;md5=8975de00e0aab10867abf36434958a28"

SRC_URI[sha256sum] = "55af7906a2c75ce6cb280a584776080602444f75815a7aff4d287bb2d7018b32"

inherit pypi setuptools3

PYPI_PACKAGE = "python-xlib"

RDEPENDS:${PN} = " \
    python3-six \
    python3-xml \
"

BBCLASSEXTEND = "native nativesdk"
