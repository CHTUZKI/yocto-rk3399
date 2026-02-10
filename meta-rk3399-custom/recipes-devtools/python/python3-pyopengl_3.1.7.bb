SUMMARY = "Python bindings for OpenGL"
DESCRIPTION = "PyOpenGL is the most common cross platform Python binding to OpenGL and related APIs"
HOMEPAGE = "https://pyopengl.sourceforge.net/"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://license.txt;md5=943332dbb441a49d1576fe75197d6cac"

SRC_URI[sha256sum] = "eef31a3888e6984fd4d8e6c9961b184c9813ca82604d37fe3da80eb000a76c86"

inherit pypi setuptools3

PYPI_PACKAGE = "PyOpenGL"

RDEPENDS:${PN} = " \
    python3-ctypes \
    python3-logging \
    python3-numpy \
    mesa \
    libglu \
"

BBCLASSEXTEND = "native nativesdk"
