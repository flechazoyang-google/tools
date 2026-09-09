# Toolbox 发布规范

> 适用范围：`com.flechazo.toolbox` 重写版（v1.1.x 起）。
> 分发渠道：**仅 GitHub Releases**（不使用 CDN，不推 gitee）。
> 版本号：沿用重写版线（`1.1.1` → `1.1.2` → …），不承接旧 `com.example.toolbox` 的 1.4.x。

---

## 一、一句话流程

```powershell
# 1. 写好 releases/v1.1.2/RELEASE_NOTE.md
# 2. 一条命令发版
.\scripts\release.ps1 -Version 1.1.2
```

脚本自动完成：改 `versionCode/versionName` → 单测 → `assembleRelease` → 校验 APK 版本号与签名 →
复制到 `releases/v1.1.2/Toolbox-v1.1.2.apk` → 更新 `RELEASE_LOG.md` → commit + tag + push →
`gh release create` 附 APK → 触发个人网站数据更新。

预览不落盘：

```powershell
.\scripts\release.ps1 -Version 1.1.2 -DryRun
```

---

## 二、版本号规则

| 项 | 规则 |
|---|---|
| `versionName` | SemVer `主.次.修`，如 `1.1.2`；只发正式版，不带 `-beta` |
| `versionCode` | **每次对外构建 +1**，绝不回退（否则 Android 拒绝覆盖安装） |
| tag | `v<versionName>`，如 `v1.1.2` |
| 修改位置 | `app/build.gradle.kts` 的 `defaultConfig`（由脚本自动改） |

---

## 三、本地归档结构

```
releases/
├── RELEASE_LOG.md                 # 累计发布日志（进 git）
└── v1.1.2/
    ├── Toolbox-v1.1.2.apk         # 本地留存（*.apk 已被 .gitignore 忽略）
    └── RELEASE_NOTE.md            # 版本说明（进 git）
```

- 目录名 = tag 名（`v1.1.2`），APK 名 = `Toolbox-v<版本>.apk`
- **APK 不进 git**，只提交 `RELEASE_NOTE.md` 与 `RELEASE_LOG.md`
- 历史版本目录永久保留，便于回滚与追溯

---

## 四、`RELEASE_NOTE.md` 模板

```markdown
# Toolbox v1.1.2

发布日期：YYYY-MM-DD
Version Code：4

## 变更内容

- ✨ 新功能
- 🐛 修复
- 🛠 工程改动

## 校验

- 单测：N 用例 / 0 失败
- 构建：assembleRelease BUILD SUCCESSFUL
- 实机：安装 release 包冒烟通过
```

内容会**原样**作为 GitHub Release 的说明，因此写得面向用户（不要写内部实现细节）。

---

## 五、App 内的「检查更新」

| 项 | 值 |
|---|---|
| 数据源 | `https://api.github.com/repos/flechazoyang-google/tools/releases/latest` |
| 仓库坐标 | `app/build.gradle.kts` 的 `buildConfigField("String", "UPDATE_REPO", ...)` |
| 比对方式 | `tag_name` 去掉 `v` 后与 `BuildConfig.VERSION_NAME` 逐段数值比较 |
| 触发时机 | 启动时自动（距上次 > 24h）+「我的 → 检查更新」手动 |
| 下载 | 打开 Release 里第一个 `.apk` 资产的 `browser_download_url` |

发布后**无需改任何配置**——`gh release create` 一建，App 下一次检查即可发现。

---

## 六、网站联动

- 网站仓库：`flechazoyang-google/personal-website`（GitHub Pages）
- `scripts/update-projects.js` 通过 GitHub API 拉取最新 Release，写入 `projects.json` 的版本号与 APK 下载链接
- `.github/workflows/update-projects.yml` 每 6 小时自动跑一次；发布脚本会用
  `gh workflow run update-projects.yml --repo flechazoyang-google/personal-website` 立即触发
- 触发失败不影响发布，网站最多 6 小时后自动同步

---

## 七、失败与回滚

| 情况 | 处理 |
|---|---|
| 单测/构建失败 | 脚本中止，版本号未提交（工作区可 `git checkout app/build.gradle.kts` 还原） |
| 推送成功但 Release 创建失败 | 手动补：`gh release create v1.1.2 releases/v1.1.2/Toolbox-v1.1.2.apk --notes-file releases/v1.1.2/RELEASE_NOTE.md --latest` |
| 发布后发现严重问题 | ① 不改已有 tag：直接发下一个 `1.1.3` 修复；② 若必须撤包：`gh release delete v1.1.2 --yes` 并删除网站上的链接 |
| tag 打错 | `git tag -d v1.1.2 && git push origin :refs/tags/v1.1.2` |

> **永远不要**删除或替换已经发布过的 `versionCode`：已装用户将无法升级。

---

## 八、发布前检查清单

- [ ] 功能已在真机/模拟器冒烟（尤其图片工具、通知、权限）
- [ ] `releases/v<版本>/RELEASE_NOTE.md` 已写好，面向用户可读
- [ ] `gh auth status` 正常，对 `flechazoyang-google/tools` 有 `repo` 权限
- [ ] `keystore/toolbox-release.jks` 与 `keystore.properties` 存在且已备份（**丢失即无法升级**）
- [ ] 工作区干净、在 `main` 分支
