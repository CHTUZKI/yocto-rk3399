SUMMARY = "LinuxCNC CNC controller and all dependencies"
DESCRIPTION = "Packagegroup that provides LinuxCNC CNC machine controller with \
all required dependencies. LinuxCNC can control milling machines, lathes, \
3D printers, laser cutters, plasma cutters, robot arms, and more."
LICENSE = "MIT"

PACKAGE_ARCH = "${MACHINE_ARCH}"

inherit packagegroup features_check

REQUIRED_DISTRO_FEATURES = "x11"

# Core LinuxCNC package
RDEPENDS:${PN} = " \
    linuxcnc \
    linuxcnc-configs \
"

# Python dependencies for LinuxCNC GUI
RDEPENDS:${PN} += " \
    python3 \
    python3-core \
    python3-tkinter \
    python3-numpy \
    python3-cairo \
    python3-pygobject \
    python3-xml \
    python3-netclient \
    python3-logging \
    python3-threading \
"

# TCL/TK dependencies for classic interfaces
RDEPENDS:${PN} += " \
    tcl \
    tk \
"

# Graphics and display dependencies
RDEPENDS:${PN} += " \
    mesa-utils \
    gtk+3 \
    libepoxy \
    glu \
"

# System utilities required by LinuxCNC
RDEPENDS:${PN} += " \
    procps \
    psmisc \
    udev \
"

# Communication and hardware interface libraries
RDEPENDS:${PN} += " \
    libmodbus \
    libgpiod \
    libgpiod-tools \
    libusb1 \
    libtirpc \
"

# Boost libraries (for Python bindings)
RDEPENDS:${PN} += " \
    boost \
"

# Optional but recommended packages for full functionality
RRECOMMENDS:${PN} = " \
    librsvg \
    python3-pillow \
    tclx \
    bwidget \
    tclreadline \
    python3-pyopengl \
    python3-configobj \
    python3-xlib \
    libgtksourceview4 \
"

# Development tools (optional, for creating custom HAL components)
RRECOMMENDS:${PN} += " \
    gcc \
    make \
"
