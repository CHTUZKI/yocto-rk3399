REQUIRED_DISTRO_FEATURES:class-native = ""

DEPENDS:remove:class-native = "virtual/libx11 libxt"
PACKAGECONFIG:remove:class-native = "xft xss"

EXTRA_OECONF:class-native = "\
    --enable-threads \
    --without-x \
    --with-tcl=${STAGING_BINDIR}/crossscripts \
    --libdir=${libdir} \
"
