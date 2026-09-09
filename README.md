# Toolbox 工具箱

一款本地优先的 Android 工具箱，27 个高频小工具，无广告、无追踪。除汇率外全部离线可用。

> 当前版本 **v1.1.1** · 详见 [docs/RELEASE-v1.1.1.md](docs/RELEASE-v1.1.1.md)
>
> 项目文档：[重写方案](docs/REWRITE_PLAN.md) · [UI 重设计](docs/UI_REDESIGN_PLAN.md) · [修复方案](docs/FIX_PLAN.md)

## 工具清单（27）

| 分类 | 工具 |
|---|---|
| 计算 | 计算器（含括号/百分号/历史）、单位换算（7 类 37 单位）、汇率换算、BMI 计算器、金额大写 |
| 图片 | 九宫格切图、二维码生成与识码、图片压缩（JPEG/PNG/WebP）、图片拼接、取色器、加水印、拼豆图纸 |
| 文本 | Base64 编解码、时间戳转换、文本差异对比、密码生成器 |
| 生活 | 倒数日（当天提醒）、番茄钟（统计持久化）、亲戚称呼（双向）、经期记录（月历预测）、做个决定（转盘/抽签/随机数）、手持弹幕 |
| 测量 | 尺子、水平仪、指南针、设备信息 |
| 安全 | 密码箱（PBKDF2 + AES-256-GCM） |

## 技术栈

- Kotlin 1.9.22 / Jetpack Compose (BOM 2024.09.00) / Material 3
- Hilt (KSP) · Room · DataStore · Retrofit + Gson · ZXing · ExifInterface
- minSdk 26 / targetSdk 34 · R8 混淆 + 资源压缩 · release 约 2 MB

## 架构

```
com.flechazo.toolbox
├── core/
│   ├── designsystem/   主题 token + 共享组件（ToolScaffold/SectionCard/ResultCard/KeyValueRow/…）
│   ├── registry/       ToolCatalog 声明式元数据，驱动首页、搜索、分类、收藏与导航
│   ├── data/           SettingsRepository / ToolsStateRepository / PeriodRepository /
│   │                   PomodoroStatsRepository / LegacyImporter
│   ├── notify/         倒数日提醒（通知渠道 + AlarmManager + 广播接收器）
│   └── util/           图片加载/保存（下采样 + EXIF + MediaStore）、尺寸计算
└── feature/<tool>/     Screen + @HiltViewModel + 不可变 UiState + StateFlow
```

## 构建

```bash
./gradlew :app:assembleDebug     # Debug APK
./gradlew :app:assembleRelease   # Release APK（需 keystore.properties）
./gradlew :app:testDebugUnitTest # 单元测试（71 个）
```

`keystore.properties` 与 `keystore/` 已被 `.gitignore` 排除，请勿提交。

## 设计原则

- **本地优先**：密码箱、倒数日、经期、番茄钟等数据全部留在设备上
- **算法可测**：所有换算/求值/预测逻辑抽成纯函数并有单测覆盖
- **失败可解释**：每个工具都有 loading / error / empty 三态，错误给可操作提示
- **内存安全**：所有图片解码统一下采样 + 输出尺寸上限，杜绝 OOM
