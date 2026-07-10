# Toolbox v1.4.2 — Bug修复与交互动效

一款简约实用的 Android 工具箱应用，集成 17 种日常工具，基于 Kotlin + Jetpack Compose 构建。

### 🐛 修复

- **密码箱解锁无反应**：`unlockedUntilMs` 改为 Compose 可观测状态，解锁/指纹/锁定后 UI 正确切换
- **API 24-25 振动崩溃**：`VibrationEffect` 需 API 26+，低版本回退旧 `vibrate(ms)` 方法
- **倒数日显示文本错误**："距离XX已经N天" → "距离XX还有N天"
- **编辑倒数日日期陈旧**：`EditCountdownDialog` 用 `key(entity.id)` 强制重组，切换实体时日期正确重置
- **密钥库初始化崩溃**：`CryptoHelper.init` 异常未捕获导致无硬件密钥库设备启动崩溃，现已 try-catch

### ✨ 动效增强

- **列表增删重排动画**：首页/收藏/密码箱/倒数日的 LazyColumn/LazyRow 均添加 `animateItem()`
- **按钮按压反馈**：`CommonButton` 按下缩放 0.97x，与卡片风格统一
- **底部导航图标渐变**：选中/未选中图标切换增加 crossfade 过渡
- **搜索清除按钮动画**：缩放+淡入淡出，出现消失更平滑

### 🎨 UX 改进

- **首页收藏入口**：只要有收藏就显示"查看全部"按钮（原仅 >3 才显示）
- **Theme 引用规范化**：`isSystemInDarkTheme()` 改为 import 调用

### 📦 下载

- [GitHub](https://github.com/flechazoyang-google/tools/releases/download/v1.4.2/toolbox-1.4.2.apk)
- [Gitee](https://gitee.com/yang-genhao/tools/releases/download/v1.4.2/toolbox-1.4.2.apk)
