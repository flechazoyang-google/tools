# Toolbox v1.4.1 — 交互反馈与UI完善

一款简约实用的 Android 工具箱应用，集成 17 种日常工具，基于 Kotlin + Jetpack Compose 构建。

### ✨ 新增
- Snackbar 操作反馈：收藏/删除/编辑后底部弹出提示，操作清晰可见
- 滑动删除：密码箱列表、倒数日列表支持左滑删除（红色背景+删除图标）
- 震感反馈：收藏、删除、计算器按钮增加触感，交互更真实
- 键盘优化：搜索栏 Enter 键（ImeAction.Search）可收起键盘，通用文本框支持键盘动作

### 🛠 修复/优化
- 分类标签触控目标 32dp→40dp，经期日历格子 32dp→40dp，更易点击
- 设置页硬编码标题替换为 TopBar 组件，统一导航风格
- 全局 Scaffold 添加 IME 键盘边距，键盘不遮挡输入框
- 组件规格统一：圆角 20dp→16dp，按钮高度 64dp→56dp

### 🎨 UI/UX
- 数据持久化：计算器表达式、搜索输入在屏幕旋转后保留（rememberSaveable）
- 空状态图标统一为 56dp + 40% 透明度
- 密码箱空状态图标从 48dp 升级至 56dp

### 📦 下载

- [GitHub](https://github.com/flechazoyang-google/tools/releases/download/v1.4.1/toolbox-1.4.1.apk)
- [Gitee](https://gitee.com/yang-genhao/tools/releases/download/v1.4.1/toolbox-1.4.1.apk)
