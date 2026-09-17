# 用户皮肤目录说明（V18.9）

`default` 皮肤已经内置在 APK 的 `app/src/main/res/drawable-nodpi/` 中，首次安装无需复制任何皮肤文件即可正常显示棋盘和棋子。

用户新增皮肤请放到手机：

`/storage/emulated/0/nodexq/pic/<皮肤名>/`

例如：`/storage/emulated/0/nodexq/pic/111/`，菜单中会显示为 `111`。

每个用户皮肤文件夹需包含 15 张图片，基础名固定为：

- `board`
- 黑方：`br bn bb ba bk bc bp`
- 红方：`rr rn rb ra rk rc rp`

支持 `.png`、`.webp`、`.jpg`、`.jpeg`。不要建立名为 `default` 的外部皮肤文件夹，因为 `default` 保留给 APK 内置皮肤。

皮肤设置中可调整棋子大小，并使用“动态校准”：开启后会显示完整 9×10 落点网格；四角黄色圆点可反复拖动微调，按住网格内部可整体平移，调整结果按皮肤独立保存。
