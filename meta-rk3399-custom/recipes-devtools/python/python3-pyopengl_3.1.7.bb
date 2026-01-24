SUMMARY = "Python bindings for OpenGL"
DESCRIPTION = "PyOpenGL is the most common cross platform Python binding to OpenGL and related APIs"
HOMEPAGE = "https://pyopengl.sourceforge.net/"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://license.txt;md5=4e61a02e19b1e1be8a29eaf399c5eb00"

SRC_URI[sha256sum] = "eef31a3888e6984fd4569343f5a948d9d5e6af6f8d29f5a6c1e9d39a3b4ba5c3"

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
