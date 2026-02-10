PACKAGECONFIG:append:pn-python3-native = " tk"

python __anonymous() {
    if (d.getVar('PN') or '') == 'python3-native':
        d.setVarFlag('PACKAGECONFIG', 'tk', ',,tk-native')
}

DEPENDS:remove:pn-python3-native = "tk"
DEPENDS:append:pn-python3-native = " tk-native"
