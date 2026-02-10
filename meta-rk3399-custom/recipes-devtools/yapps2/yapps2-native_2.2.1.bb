SUMMARY = "Yet Another Python Parser System"
DESCRIPTION = "Yapps is a Python parser generator that generates LL(1) parsers"
HOMEPAGE = "https://github.com/smurfix/yapps"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=75db4afbd75feb0b856fb4fade51aad2"

SRC_URI = "git://github.com/smurfix/yapps.git;branch=master;protocol=https"
SRCREV = "67541062093846bb53f011da0f4d489d63375d2d"

S = "${WORKDIR}/git"

inherit native python3native setuptools3

BBCLASSEXTEND = "native"
