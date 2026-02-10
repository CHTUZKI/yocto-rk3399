# 对 python3-native 禁用 tk，打破 tk <-> python3-native 循环依赖
# （target 的 Python 仍可通过默认 PACKAGECONFIG 保留 tk，供 LinuxCNC 等使用）
PACKAGECONFIG:remove:pn-python3-native = "tk"
