# Toolbox 发布日志

> 每次发布在顶部追加一条。详细说明见 `releases/v<版本>/RELEASE_NOTE.md`。
> APK 仅本地留存（`*.apk` 已被 gitignore），对外分发走 GitHub Releases。

---

## v1.1.1 (2026-09-09)

实机反馈修复：图片读取、计算器布局、金额大写换行、弹幕/尺子横屏、倒数日日历选择、输入框统一。

- 修复所有图片工具「无法读取该图片」（`inJustDecodeBounds` 下 `decodeStream` 必然返回 null 被当成失败）
- 计算器键盘固定底部，历史记录不再挤走键盘
- 大写金额允许换行，长金额不再被省略号截断
- 手持弹幕全屏锁定横屏 + 顶栏常驻入口；尺子改为横屏贴边 + 刻度数字
- 倒数日改用日历选择日期（原为手输字符串）
- 新增 `ToolTextField`，全站 14 文件 24 处输入框统一为填充式圆角

- Version Code：3
- 单测：76 用例 / 0 失败

---

## v1.1.0 (2026-09-09)

生产级修复版：正确性、崩溃与内存、设计一致性、功能补齐四个阶段。

- 金额大写算法重写（`BigDecimal`），修正分/角与零插入错误
- 旧版密码导入修复（`pendingPasswords` 从未赋值导致永远导入 0 条）
- 图片解码下采样 + EXIF 纠正 + MediaStore `IS_PENDING`，消除 OOM 与假成功
- 补齐设计 token（`surfaceContainerLow` 等），深色状态栏与硬编码色清理
- 新增倒数日提醒、经期月历、番茄钟持久化、密码箱自动锁、亲戚反推、数据导出
- 单测 35 → 71

- Version Code：2
- 单测：71 用例 / 0 失败

---

## v1.0.0 (2026-09-08)

`com.flechazo.toolbox` 彻底重写版首版，27 个工具。

- 架构统一为 Screen + `@HiltViewModel` + 不可变 UiState + StateFlow
- 设计系统、声明式工具注册表、DataStore 仓库层
- 单测 35 用例

- Version Code：1
