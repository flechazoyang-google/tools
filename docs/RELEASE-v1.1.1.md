# Toolbox v1.1.1 发布说明

> 版本：1.1.1（versionCode 3）
> 基线：v1.1.0
> 主题：**实机反馈修复**——按真机使用中发现的问题逐条修正

---

## 一、图片功能全部不可用（严重）

**现象**：所有图片工具选图后提示"无法读取该图片"。

**根因**：`ImageUtils.loadScaled` 里用 `BitmapFactory.decodeStream(...)` 的返回值判断成败，
但 `inJustDecodeBounds = true` 时该方法**必然返回 null**（只填充 bounds），
于是 `?: return@withContext null` 永远命中，任何图片都读不出来。

**修复**：改为先判断 `openInputStream` 是否为 null，再取 bounds；解码结果单独判空。

**验证**：模拟器实机选择一张 1254×1254 / 1.16 MB 的 PNG，
图片压缩页正确显示预览、原始尺寸、原始大小与输出尺寸。

---

## 二、计算器布局

**现象**：无历史记录时键盘偏上；历史记录多时键盘被挤出屏幕。

**修复**：`ToolScaffold(scrollable = false)` + 显示区/历史区 `weight(1f)` 内部滚动，
键盘区固定在底部。历史不再挤走键盘。

**验证**：实机键盘按键纵跨 y=1345…2235（屏幕高 2340），稳定贴底。

## 三、金额大写显示不全

**现象**：数字较大时中文大写被省略号截断。

**修复**：结果卡改用 `singleLine = false` 自动换行。

**验证**：输入 `1234567890123.45` 输出完整
"壹万亿贰仟叁佰肆拾伍亿陆仟柒佰捌拾玖万零壹佰贰拾叁元肆角伍分"。

## 四、手持弹幕全屏未横屏

**修复**：进入全屏时 `requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE`，
退出恢复；顶栏新增常驻全屏入口（底部按钮在内容长时需滚动才能点到）。
同时给 Activity 加 `configChanges`，避免旋转重建导致全屏状态丢失。

**验证**：实机进入全屏后根布局尺寸 [0,0][2340,1080]（横屏）。

## 五、倒数日日期改为日历选择

**修复**：手输 `yyyy-MM-dd` 改为只读日期行 + M3 `DatePickerDialog`，
显示"2026-09-09 星期三"；初值按 UTC 当天 00:00 处理，避免时区偏移一天。

**验证**：实机点击日期行弹出日历（September 2026）。

## 六、尺子横屏贴边 + 刻度数字

**修复**：改为横屏展示；刻度从屏幕最左边缘开始（贴边便于对准物体）；
厘米模式每格 1mm、英寸模式每格 1/16 in；主刻度标注数字；
刻度区可横向滚动，最长 30cm / 12in；单位切换固定在底部。

**验证**：实机进入尺子后根布局 [0,0][2340,1080]（横屏），显示厘米/英寸切换。

## 七、输入框/选择框观感简陋

**修复**：新增设计系统组件 `ToolTextField`——填充式 + 14dp 圆角，
去掉生硬的外框线条，聚焦时仅以主色下划线强调，错误态用 error 容器色；
`LabeledDropdown` 同步改为同款填充样式。
全站 14 个文件、24 处输入框统一替换，已无遗留 `OutlinedTextField`。

---

## 验收

```
.\gradlew.bat :app:testDebugUnitTest --offline  → BUILD SUCCESSFUL, 71 tests, 0 failures
.\gradlew.bat :app:assembleRelease  --offline   → BUILD SUCCESSFUL
```

模拟器（Android 14 / API 34）实机验证上述 7 项，`logcat -b crash` 无记录。
