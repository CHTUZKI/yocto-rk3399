SUMMARY = "Yet Another Python Parser System"
DESCRIPTION = "Yapps is a Python parser generator that generates LL(1) parsers"
HOMEPAGE = "https://github.com/smurfix/yapps"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d32239bcb673463ab874e80d47fae504"

SRC_URI = "git://github.com/smurfix/yapps.git;branch=master;protocol=https"
SRCREV = "eb58c18283fdc04217e6a4dd2a3b2a1c47a7ff64"

S = "${WORKDIR}/git"

inherit native python3native setuptools3

BBCLASSEXTEND = "native"
