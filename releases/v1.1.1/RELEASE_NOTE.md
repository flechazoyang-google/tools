# Toolbox v1.1.1

发布日期：2026-09-09
Version Code：3
详细说明：`docs/RELEASE-v1.1.1.md`

## 变更内容

- 修复所有图片工具提示「无法读取该图片」——`inJustDecodeBounds = true` 时 `decodeStream` 必然返回 null，旧代码把它当成失败判据
- 计算器键盘固定底部（`ToolScaffold(scrollable = false)` + 显示区 `weight(1f)` 内部滚动），历史记录不再挤走键盘
- 大写金额结果卡改为可换行，长金额不再被省略号截断
- 手持弹幕全屏时锁定横屏，顶栏新增常驻全屏入口；Activity 声明 `configChanges` 避免旋转重建
- 尺子改为横屏、刻度从屏幕最左边缘起画、主刻度标注数字、可横向滚动 30cm / 12in
- 倒数日添加事件改为日历选择日期（只读日期行 + `DatePickerDialog`，UTC 初值）
- 新增 `ToolTextField`（填充式 + 14dp 圆角），全站 14 个文件 24 处输入框统一替换

## 校验

- 单测：76 用例 / 0 失败（`.\gradlew.bat :app:testDebugUnitTest --offline`）
- 构建：`assembleRelease` BUILD SUCCESSFUL
- 实机：Android 14 模拟器安装 release 包，图片选图/保存、弹幕全屏、尺子横屏、倒数日日历均通过，`logcat -b crash` 无记录
