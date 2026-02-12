EXTRA_OEMESON:append = " -Dglx=dri"

do_install:append () {
    if [ -d ${D}${libdir} ]; then
        if [ -e ${D}${libdir}/libGL.so.1 ] && [ ! -e ${D}${libdir}/libGLX.so.0 ]; then
            ln -sf libGL.so.1 ${D}${libdir}/libGLX.so.0
        fi
        if [ -e ${D}${libdir}/libGL.so.1 ] && [ ! -e ${D}${libdir}/libOpenGL.so.0 ]; then
            ln -sf libGL.so.1 ${D}${libdir}/libOpenGL.so.0
        fi
    fi
}

FILES:libgl-mesa:append = " ${libdir}/libGLX.so.* ${libdir}/libOpenGL.so.*"
