# Toolbox

一款简约实用的 Android 工具箱应用，集成多种日常工具。

## 功能

| 工具 | 说明 |
|------|------|
| 🧮 计算器 | 四则运算与括号计算 |
| 💱 汇率换算 | 本地汇率实时换算 |
| 🌐 IP 属地查询 | 查询 IP 地址归属地信息 |
| 📅 倒数日 · 纪念日 | 支持倒数日、纪念日、生日三种类型 |
| 🔐 密码箱 | 本地加密存储密码 |
| 🏋️ BMI 计算器 | 身体质量指数计算 |
| 🖼️ 九宫格切图 | 图片切成 3×3 九宫格并保存到相册 |
| ⏱ 番茄钟 | 专注计时（25+5 工作法），支持短休/长休 |
| 🧩 拼豆图纸 | 图片转拼豆像素图纸，支持多种网格密度 |
| 🔤 Base64 编解码 | 文本 Base64 编码/解码、一键复制 |
| ⏰ 时间戳转换 | Unix 时间戳与日期互转 |

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material3
- **架构**: MVVM + Hilt DI
- **数据库**: Room
- **网络**: Retrofit + OkHttp
- **导航**: Navigation Compose
- **最低 SDK**: Android 7.0 (API 24)

## 构建

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/`

## 版本历史

- **1.2.2** — 修复颜色过暗、图标循环引用、更新检查改用 Gitee 源
- **1.2.1** — 检查更新功能（Gitee + GitHub 双源下载）、应用图标更换
- **1.2.0** — 工具分类标签栏、UI 现代化重构
- **1.1.x** — 新增拼豆图纸、IP 查询修复、UI 优化
- **1.0.0** — 初始版本
