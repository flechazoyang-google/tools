# Toolbox v1.1.2

发布日期：2026-09-09
Version Code：4

## 变更内容

- ✨ 新增「检查更新」：启动时自动检查（每 24 小时一次），「我的」页新增手动检查入口
- ✨ 更新信息直接读取 GitHub Releases（`releases/latest`），显示版本号、更新说明与 APK 下载直链
- ✨ 发现新版本弹窗支持「立即下载」（打开 APK 资产链接）与「详情」（打开 Release 页面）
- 🛠 新增一键发版脚本 `scripts/release.ps1`：改版本号 → 单测 → clean 构建 → 校验签名与版本号 → 本地归档 → 打 tag → 推送 → 自动创建 GitHub Release → 触发网站更新
- 🛠 新增本地发版归档 `releases/v<版本>/`（APK 本地留存 + 版本说明），并建立 `releases/RELEASE_LOG.md` 累计日志
- 🐛 移除设置页底部残留的硬编码「工具箱 v1.0.0」文案（与「关于」卡片的真实版本号重复且陈旧）
- 📄 新增 `docs/RELEASE_PROCESS.md` 发布规范
- 🧪 新增版本比较与解析单测（`UpdateTest`），单测总数 76

## 校验

- 单测：76 用例 / 0 失败（`.\gradlew.bat :app:testDebugUnitTest --offline`）
- 构建：`clean` + `assembleRelease` BUILD SUCCESSFUL，APK 内版本常量校验通过
- 实机：安装 release 包，「我的 → 检查更新」可正确识别新版本并跳转下载；
  用旧版本构建验证会自动弹出「发现新版本 v1.1.2」
