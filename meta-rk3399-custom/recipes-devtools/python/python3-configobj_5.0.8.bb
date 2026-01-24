SUMMARY = "Config file reading, writing and validation"
DESCRIPTION = "ConfigObj is a simple but powerful config file reader and writer. \
It has lots of useful features for programmers and users."
HOMEPAGE = "https://github.com/DiffSK/configobj"
LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=b7e7cfd8e3f5aca51c222f5a3b5e169c"

SRC_URI[sha256sum] = "6f704434a07dc4f4dc7c9a745172c1cad449bc8a64f21f4348a3c5f2adb1d5c3"

inherit pypi setuptools3

PYPI_PACKAGE = "configobj"

RDEPENDS:${PN} = "python3-six"

BBCLASSEXTEND = "native nativesdk"
