# Toolbox v1.1.0

发布日期：2026-09-09
Version Code：2
详细说明：`docs/RELEASE-v1.1.0.md`

## 变更内容

- **正确性**：金额大写改用 `BigDecimal` 取到分（修正 `8.10`→「玖分」、`10001`→「壹万壹」）；K→°F 换算；时间戳相对时间方向；倒数日纪念日负数天数；经期日期时区；全站 `Locale.ROOT` 格式化
- **崩溃与内存**：图片采样逻辑修正（4032px 照片不再全分辨率解码）；九宫格/取景器/拼接/拼豆输出上限；二维码长文本长度预检；MediaStore `IS_PENDING`；EXIF 方向；主线程 I/O 迁出
- **设计一致性**：补齐 `surfaceContainerLow` 等色彩 token；深色状态栏；尺子/番茄钟/转盘/BMI 色带改用主题色；`ResultCard` 多行；`FeedbackBlock` 补 Success 态
- **功能补齐**：倒数日提醒（通知渠道 + AlarmManager）；经期月历 + DataStore；番茄钟锚定真实时钟 + 统计持久化；转盘绘制文字；弹幕全屏状态入 UiState；密码箱自动锁 + 搜索；设备信息补 CPU/传感器；亲戚反推；我的页导出/关于
- 单测 35 → 71

## 校验

- 单测：71 用例 / 0 失败
- 构建：`assembleRelease` BUILD SUCCESSFUL
- 实机：Android 14 模拟器 27 个工具逐一进入，无崩溃
