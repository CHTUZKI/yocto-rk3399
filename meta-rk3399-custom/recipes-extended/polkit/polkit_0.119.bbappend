# Use GitLab mirror as alternative source for polkit
# Original source: http://www.freedesktop.org/software/polkit/releases/polkit-${PV}.tar.gz
# GitLab mirror: https://gitlab.freedesktop.org/polkit/polkit

# Override SRC_URI to use GitLab archive instead of freedesktop.org
SRC_URI = " \
    https://gitlab.freedesktop.org/polkit/polkit/-/archive/${PV}/polkit-${PV}.tar.gz \
    ${@bb.utils.contains('DISTRO_FEATURES', 'pam', '${PAM_SRC_URI}', '', d)} \
    file://0001-pkexec-local-privilege-escalation-CVE-2021-4034.patch \
    file://0002-CVE-2021-4115-GHSL-2021-077-fix.patch \
    file://0003-Added-support-for-duktape-as-JS-engine.patch \
    file://0004-Make-netgroup-support-optional.patch \
    file://CVE-2025-7519.patch \
"

# GitLab archive has different SHA256 than freedesktop.org release
# Calculated SHA256 for GitLab archive: 6930f4a4f9f6b30ce027b6d1fa09ab71132f250565cd213b36a4634eb133b59a
SRC_URI[sha256sum] = "6930f4a4f9f6b30ce027b6d1fa09ab71132f250565cd213b36a4634eb133b59a"

