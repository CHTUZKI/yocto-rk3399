SUMMARY:append = " (trimmed for rk3399-firefly)"

# 去掉一些不必要的第三方桌面小工具
# - xfce4-notes-plugin: 便签/Notes 插件
# - gigolo: 远程文件系统管理器
# - catfish: 图形化搜索工具
# - xfce4-mailwatch-plugin: 邮件监视
# - xfce4-smartbookmark-plugin: 浏览器快捷搜索相关插件
RRECOMMENDS:${PN}:remove = " \
    xfce4-notes-plugin \
    gigolo \
    catfish \
    xfce4-mailwatch-plugin \
    xfce4-smartbookmark-plugin \
"

