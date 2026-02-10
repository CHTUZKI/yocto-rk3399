SUMMARY = "LinuxCNC - Enhanced Machine Controller for CNC machines"
DESCRIPTION = "LinuxCNC is a free, open source CNC machine controller. \
It can drive milling machines, lathes, 3D printers, laser cutters, plasma \
cutters, robot arms, hexapods, and more. \
LinuxCNC relies on a realtime kernel (PREEMPT_RT) to support real-time motion control."
HOMEPAGE = "https://linuxcnc.org/"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=b234ee4d69f5fce4486a80fdaf4a4263"

SECTION = "misc"

# LinuxCNC source from GitHub - using 2.9 branch for stability
SRC_URI = "git://github.com/LinuxCNC/linuxcnc.git;branch=2.9;protocol=https \
           file://linuxcnc.conf \
           file://linuxcnc-rtapi-ioperm-return-int.patch \
           file://linuxcnc-hm2-eth-sockaddr-cast.patch \
           file://linuxcnc-fix-environ-decl.patch \
           "

# Use AUTOREV for development, pin to specific commit for production
SRCREV = "${AUTOREV}"
PV = "2.9+git${SRCPV}"

S = "${WORKDIR}/git"

inherit pkgconfig python3native python3-dir gettext features_check

# Ensure configure can find ps via hosttools
HOSTTOOLS += "ps"

# Build dependencies
DEPENDS = " \
    autoconf-native \
    automake-native \
    intltool-native \
    asciidoc-native \
    docbook-xsl-stylesheets-native \
    libxslt-native \
    boost \
    gtk+3 \
    gtk+ \
    libmodbus \
    libgpiod \
    readline \
    libtirpc \
    libusb1 \
    libxmu \
    libepoxy \
    mesa \
    libglu \
    python3 \
    python3-native \
    yapps2-native \
    tcl \
    tk \
    libxinerama \
    glib-2.0 \
    virtual/libgl \
"

# Runtime dependencies
RDEPENDS:${PN} = " \
    python3 \
    python3-core \
    python3-tkinter \
    python3-numpy \
    python3-pycairo \
    python3-pygobject \
    gtk+ \
    tcl \
    tk \
    tk-lib \
    bash \
    mesa-demos \
    procps \
    psmisc \
    udev \
    boost \
    libmodbus \
    libgpiod \
    libusb1 \
    libtirpc \
"

# Recommended packages (optional but useful)
RRECOMMENDS:${PN} = " \
    librsvg \
    python3-pillow \
    tclx \
    bwidget \
"

# LinuxCNC builds in src subdirectory
B = "${S}/src"

# Export necessary environment variables
export PYTHON = "${PYTHON3}"

BOOST_PYTHON_LIBNAME = "boost_python${@d.getVar('PYTHON_BASEVERSION').replace('.', '')}"

do_configure() {
    cd ${S}/src

    if [ -f ${S}/src/configure.ac ]; then
        if ! grep -q "skipped (cross-compile)" ${S}/src/configure.ac; then
            perl -0777 -pi -e 's/(AC_MSG_CHECKING\(\[match between tk and Tkinter versions\]\)\n)/$1if test "\$cross_compiling" = yes; then\n    AC_MSG_RESULT([skipped (cross-compile), using Tcl \$TCL_VERSION \/ Tk \$TK_VERSION])\nelse\n\n/s; s/(AC_MSG_RESULT\(\[\$PYTHON_TK_VERSION\]\)\n)/$1\nfi\n/s' ${S}/src/configure.ac
        fi
    fi

    if [ -f ${S}/docs/src/Submakefile ]; then
        sed -i \
            -e 's/^manpages: .*/manpages:/' \
            -e '/^TARGETS \+= manpages$/d' \
            -e '/^\$(DOC_DIR)\/man\/%: \$(DOC_DIR)\/src\/man\/%\.adoc$/,/^\t\$</c\$(DOC_DIR)\/man\/%: \$(DOC_DIR)\/src\/man\/%\.adoc\n\t@:' \
            ${S}/docs/src/Submakefile
    fi
     
    # Run autogen to generate configure script
    ./autogen.sh
    
    # Configure for userspace realtime (PREEMPT_RT)
    ./configure \
        --prefix=${prefix} \
        --with-realtime=uspace \
        --disable-build-documentation \
        --disable-check-runtime-deps \
        --enable-non-distributable \
        --with-tclConfig=${STAGING_LIBDIR}/tclConfig.sh \
        --with-tkConfig=${STAGING_LIBDIR}/tkConfig.sh \
        --host=${HOST_SYS} \
        --build=${BUILD_SYS} \
        PYTHON=${PYTHON}

    if [ -f ${S}/src/Makefile.inc ]; then
        sed -i \
            -e "/^TCL_CFLAGS=/ s|-I/usr/include/tcl8\\.6|-I${RECIPE_SYSROOT}/usr/include/tcl8.6|g" \
            -e "/^TCL_CFLAGS=/ s|-I/usr/include\b|-I${RECIPE_SYSROOT}/usr/include|g" \
            -e "/^TCL_LIBS=/ s|-L/usr/lib\b|-L${RECIPE_SYSROOT}/usr/lib|g" \
            ${S}/src/Makefile.inc
    fi

    if [ -f ${S}/src/Makefile ]; then
        sed -i \
            -e 's/\$(Q)ld -d -r/\$(Q)\$(LD) -d -r/' \
            ${S}/src/Makefile
    fi
}

do_compile() {
    cd ${S}/src
    oe_runmake \
        "ULFLAGS=${ULFLAGS} -I${RECIPE_SYSROOT}/usr/include/tcl8.6 -I${RECIPE_SYSROOT}/usr/include" \
        "RTFLAGS=${RTFLAGS} -I${S}/src/rtapi -I${S}/src/hal" \
        "BOOST_PYTHON_LIB=-l${BOOST_PYTHON_LIBNAME}"
}

do_install() {
    cd ${S}/src
    oe_runmake DESTDIR=${D} install

     if [ -d ${D}${prefix}/etc/linuxcnc ]; then
         install -d ${D}${sysconfdir}
         mv ${D}${prefix}/etc/linuxcnc ${D}${sysconfdir}/ || true
         rmdir --ignore-fail-on-non-empty ${D}${prefix}/etc || true
         rmdir --ignore-fail-on-non-empty ${D}${prefix} || true
     fi
 
     if [ -d ${D}/lib/linuxcnc ]; then
        install -d ${D}${libdir}
        if [ ! -e ${D}${libdir}/linuxcnc ]; then
            mv ${D}/lib/linuxcnc ${D}${libdir}/
        fi
        rmdir --ignore-fail-on-non-empty ${D}/lib/linuxcnc || true
        rmdir --ignore-fail-on-non-empty ${D}/lib || true
    fi
    
    # Install configuration file
    install -d ${D}${sysconfdir}/linuxcnc
    if [ -f ${WORKDIR}/linuxcnc.conf ]; then
        install -m 0644 ${WORKDIR}/linuxcnc.conf ${D}${sysconfdir}/linuxcnc/
    fi
    
    # Install sample configs
    install -d ${D}${datadir}/linuxcnc/configs
    if [ -d ${S}/configs ]; then
        cp -r ${S}/configs/* ${D}${datadir}/linuxcnc/configs/ || true
    fi
    
    # Install nc_files (G-code samples)
    install -d ${D}${datadir}/linuxcnc/nc_files
    if [ -d ${S}/nc_files ]; then
        cp -r ${S}/nc_files/* ${D}${datadir}/linuxcnc/nc_files/ || true
    fi

    if [ -n "${RECIPE_SYSROOT_NATIVE}" ]; then
        for f in $(grep -RIl "^#!" ${D} 2>/dev/null); do
            sed -i -E \
                -e '1{s|^#!.*python3-native/python3[^[:space:]]*.*$|#!/usr/bin/python3|;}' \
                "$f" || true
        done
    fi
}

# Define packages
PACKAGES = "${PN} ${PN}-dev ${PN}-staticdev ${PN}-doc ${PN}-configs ${PN}-dbg"

FILES:${PN} = " \
    ${bindir}/* \
    ${libdir}/*.so* \
    ${libdir}/linuxcnc/* \
    ${libdir}/python*/* \
    ${libdir}/tcltk/* \
    ${datadir}/linuxcnc \
    ${datadir}/linuxcnc/** \
    ${datadir}/gmoccapy \
    ${datadir}/gmoccapy/** \
    ${datadir}/axis \
    ${datadir}/axis/** \
    ${datadir}/gscreen \
    ${datadir}/gscreen/** \
    ${datadir}/glade \
    ${datadir}/glade/** \
    ${datadir}/gtksourceview-4 \
    ${datadir}/gtksourceview-4/** \
    ${datadir}/qtvcp \
    ${datadir}/qtvcp/** \
    ${datadir}/linuxcnc/hallib \
    ${datadir}/applications/* \
    ${datadir}/desktop-directories/* \
    ${datadir}/icons/* \
    ${datadir}/menus/* \
    ${datadir}/locale/* \
    ${sysconfdir}/linuxcnc/* \
    ${sysconfdir}/X11/app-defaults/TkLinuxCNC \
"

FILES:${PN}-configs = ""

ALLOW_EMPTY:${PN}-configs = "1"
RDEPENDS:${PN}-configs = "${PN}"

FILES:${PN}-doc = " \
    ${datadir}/doc/linuxcnc \
    ${mandir} \
    ${mandir}/** \
"

FILES:${PN}-staticdev = " \
    ${libdir}/liblinuxcnc.a \
"

# Skip certain QA checks
INSANE_SKIP:${PN} = "dev-so ldflags"
INSANE_SKIP:${PN}-dev = "dev-elf"

# Set proper RPATH for shared libraries
LDFLAGS += "-Wl,-rpath,${libdir}/linuxcnc"

# This recipe requires X11
REQUIRED_DISTRO_FEATURES = "x11"
