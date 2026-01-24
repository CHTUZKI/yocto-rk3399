SUMMARY = "LinuxCNC - Enhanced Machine Controller for CNC machines"
DESCRIPTION = "LinuxCNC is a free, open source CNC machine controller. \
It can drive milling machines, lathes, 3D printers, laser cutters, plasma \
cutters, robot arms, hexapods, and more. \
LinuxCNC relies on a realtime kernel (PREEMPT_RT) to support real-time motion control."
HOMEPAGE = "https://linuxcnc.org/"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=59530bdf33659b29e73d4adb9f9f6552"

SECTION = "misc"

# LinuxCNC source from GitHub - using 2.9 branch for stability
SRC_URI = "git://github.com/LinuxCNC/linuxcnc.git;branch=2.9;protocol=https \
           file://linuxcnc.conf \
           "

# Use AUTOREV for development, pin to specific commit for production
SRCREV = "${AUTOREV}"
PV = "2.9+git${SRCPV}"

S = "${WORKDIR}/git"

inherit pkgconfig python3native python3-dir gettext

# Build dependencies
DEPENDS = " \
    autoconf-native \
    automake-native \
    intltool-native \
    boost \
    gtk+3 \
    libmodbus \
    libgpiod \
    readline \
    libtirpc \
    libusb1 \
    libxmu \
    libepoxy \
    mesa \
    glu \
    python3 \
    python3-native \
    tcl \
    tk \
    libxinerama \
    glib-2.0 \
    virtual/libgl \
"

# Runtime dependencies
RDEPENDS:${PN} = " \
    python3 \
    python3-tkinter \
    python3-numpy \
    python3-cairo \
    python3-pygobject \
    tcl \
    tk \
    mesa-utils \
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
export PYTHON = "${PYTHON}"

do_configure() {
    cd ${S}/src
    
    # Run autogen to generate configure script
    ./autogen.sh
    
    # Configure for userspace realtime (PREEMPT_RT)
    ./configure \
        --prefix=${prefix} \
        --with-realtime=uspace \
        --disable-build-documentation \
        --disable-check-runtime-deps \
        --enable-non-distributable \
        --host=${HOST_SYS} \
        --build=${BUILD_SYS} \
        PYTHON=${PYTHON}
}

do_compile() {
    cd ${S}/src
    oe_runmake
}

do_install() {
    cd ${S}/src
    oe_runmake DESTDIR=${D} install
    
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
}

# Define packages
PACKAGES = "${PN} ${PN}-dev ${PN}-doc ${PN}-configs ${PN}-dbg"

FILES:${PN} = " \
    ${bindir}/* \
    ${libdir}/*.so* \
    ${libdir}/linuxcnc/* \
    ${libdir}/python*/* \
    ${libdir}/tcltk/* \
    ${datadir}/linuxcnc/*.* \
    ${datadir}/linuxcnc/hallib \
    ${datadir}/applications/* \
    ${datadir}/desktop-directories/* \
    ${datadir}/icons/* \
    ${datadir}/menus/* \
    ${datadir}/locale/* \
    ${sysconfdir}/linuxcnc/* \
"

FILES:${PN}-configs = " \
    ${datadir}/linuxcnc/configs \
    ${datadir}/linuxcnc/nc_files \
"

FILES:${PN}-doc = " \
    ${datadir}/doc/linuxcnc \
"

# Skip certain QA checks
INSANE_SKIP:${PN} = "dev-so ldflags"
INSANE_SKIP:${PN}-dev = "dev-elf"

# Set proper RPATH for shared libraries
LDFLAGS += "-Wl,-rpath,${libdir}/linuxcnc"

# This recipe requires X11
REQUIRED_DISTRO_FEATURES = "x11"
