<#
.SYNOPSIS
  Toolbox 一键发版：改版本号 → 单测 → 构建 → 本地归档 → 提交打 tag → 推送 → 建 GitHub Release。

.DESCRIPTION
  只发布正式版，只推 GitHub。执行前请确保：
    1. 当前在 main 分支且工作区干净
    2. 已写好 releases/v<版本>/RELEASE_NOTE.md（缺失时脚本会生成模板并中止）

.PARAMETER Version
  本次版本号，格式 x.y.z（如 1.1.2）。

.PARAMETER Notes
  版本说明文件路径，默认 releases/v<版本>/RELEASE_NOTE.md。

.PARAMETER SkipTests
  跳过单元测试（不建议）。

.PARAMETER DryRun
  只打印将要执行的步骤，不改文件、不提交、不推送。

.EXAMPLE
  .\scripts\release.ps1 -Version 1.1.2 -DryRun
  .\scripts\release.ps1 -Version 1.1.2
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Notes,
    [switch]$SkipTests,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$tag = "v$Version"
$notesPath = if ($Notes) { $Notes } else { Join-Path $repoRoot "releases\$tag\RELEASE_NOTE.md" }
$gradleFile = Join-Path $repoRoot 'app\build.gradle.kts'
$apkSrc = Join-Path $repoRoot 'app\build\outputs\apk\release\app-release.apk'
$apkName = "Toolbox-$tag.apk"
$apkDst = Join-Path $repoRoot "releases\$tag\$apkName"
$aapt2 = 'E:\Android\SDK\build-tools\34.0.0\aapt2.exe'
$apksigner = 'E:\Android\SDK\build-tools\34.0.0\apksigner.bat'
$websiteRepo = 'flechazoyang-google/personal-website'

# gh 可能不在当前 shell 的 PATH 上，做一次兜底解析
$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) { $gh = 'C:\Program Files\GitHub CLI\gh.exe' }

function Info($m) { Write-Host "[release] $m" -ForegroundColor Cyan }
function Warn($m) { Write-Host "[warn]    $m" -ForegroundColor Yellow }
function Die($m) { Write-Host "[error]   $m" -ForegroundColor Red; exit 1 }
function Run([scriptblock]$block) { & $block; if ($LASTEXITCODE -ne 0) { Die "上一步失败（exit $LASTEXITCODE）" } }

# ---------- 0. 参数与前置校验 ----------
if ($Version -notmatch '^\d+\.\d+\.\d+$') { Die "版本号必须形如 1.1.2，当前为 '$Version'" }

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
if ($branch -ne 'main') { Die "当前分支是 '$branch'，请切到 main 再发布" }

$dirty = @(git status --porcelain)
if ($dirty.Count -gt 0) { Die "工作区有未提交改动，请先提交或 stash：`n$($dirty -join "`n")" }

if (-not (Test-Path $gradleFile)) { Die "找不到 $gradleFile" }

$content = Get-Content $gradleFile -Raw
$curCode = [int]([regex]::Match($content, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$curName = [regex]::Match($content, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
$newCode = $curCode + 1

if ($Version -eq $curName) { Die "版本号与当前一致（$curName），无需发布" }

Info "版本：$curName (code $curCode)  ->  $Version (code $newCode)"
Info "tag：$tag     APK：releases\$tag\$apkName"

# ---------- 1. 版本说明 ----------
if (-not (Test-Path $notesPath)) {
    if ($DryRun) {
        Warn "缺少版本说明（DryRun 不创建）：$notesPath"
    } else {
        $template = @"
# Toolbox $tag

发布日期：$(Get-Date -Format 'yyyy-MM-dd')
Version Code：$newCode

## 变更内容

- （逐条列出本次变更）

## 校验

- 单测：N 用例 / 0 失败
- 构建：assembleRelease BUILD SUCCESSFUL
- 实机：安装 release 包冒烟通过
"@
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $notesPath) | Out-Null
        Set-Content -Path $notesPath -Value $template -Encoding UTF8
        Die "已生成版本说明模板，请填写内容后重新运行：$notesPath"
    }
}

# ---------- 2. 改版本号 ----------
$updated = $content -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode"
$updated = $updated -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$Version`""
if ($DryRun) {
    Info "[DryRun] 将写入 app/build.gradle.kts：versionCode=$newCode, versionName=$Version"
} else {
    Set-Content -Path $gradleFile -Value $updated -NoNewline -Encoding UTF8
}

# ---------- 3. 单测 ----------
if ($SkipTests) {
    Warn "已跳过单元测试"
} elseif ($DryRun) {
    Info "[DryRun] .\gradlew.bat :app:testDebugUnitTest --offline"
} else {
    Info "运行单元测试…"
    Run { .\gradlew.bat :app:testDebugUnitTest --offline }
}

# ---------- 4. 构建 release ----------
if ($DryRun) {
    Info "[DryRun] .\gradlew.bat :app:assembleRelease --offline"
} else {
    Info "构建 release…"
    Run { .\gradlew.bat :app:assembleRelease --offline }
}

# ---------- 5. 校验 APK ----------
if (-not $DryRun) {
    if (-not (Test-Path $apkSrc)) { Die "构建产物不存在：$apkSrc" }

    if (Test-Path $aapt2) {
        $badging = & $aapt2 dump badging $apkSrc 2>$null | Select-Object -First 1
        if ($badging -notmatch "versionCode='$newCode'") { Die "APK versionCode 与预期不符：$badging" }
        if ($badging -notmatch [regex]::Escape("versionName='$Version'")) { Die "APK versionName 与预期不符：$badging" }
        Info "APK 版本校验通过：versionCode=$newCode versionName=$Version"
    } else {
        Warn "找不到 aapt2，跳过 APK 版本校验"
    }

    if (Test-Path $apksigner) {
        & $apksigner verify $apkSrc 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) { Die "APK 签名校验失败" }
        Info "APK 签名校验通过"
    } else {
        Warn "找不到 apksigner，跳过签名校验"
    }
}

# ---------- 6. 本地归档 ----------
if ($DryRun) {
    Info "[DryRun] 复制 APK -> releases\$tag\$apkName"
} else {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $apkDst) | Out-Null
    Copy-Item $apkSrc $apkDst -Force
    Info "已归档：releases\$tag\$apkName"
}

# ---------- 7. 更新发布日志 ----------
$logPath = Join-Path $repoRoot 'releases\RELEASE_LOG.md'
if ($DryRun) {
    Info "[DryRun] 在 releases/RELEASE_LOG.md 顶部插入 $tag 条目"
} elseif (Test-Path $logPath) {
    $log = Get-Content $logPath -Raw
    if ($log -notmatch [regex]::Escape("## $tag ")) {
        $entry = "## $tag ($(Get-Date -Format 'yyyy-MM-dd'))`n`n（摘要见 releases\$tag\RELEASE_NOTE.md）`n`n- Version Code：$newCode`n`n---`n`n"
        # 插到第一个 "---" 之后
        $idx = $log.IndexOf("---")
        $newLog = if ($idx -ge 0) { $log.Insert($idx + 3, "`n`n" + $entry) } else { $log + "`n" + $entry }
        Set-Content -Path $logPath -Value $newLog -Encoding UTF8
        Info "已更新 releases/RELEASE_LOG.md"
    } else {
        Warn "RELEASE_LOG.md 已有 $tag 条目，跳过"
    }
}

# ---------- 8. 提交 + 打 tag + 推送 ----------
if ($DryRun) {
    Info "[DryRun] git add -A && git commit -m 'release: $tag'"
    Info "[DryRun] git tag -a $tag -m '$tag'"
    Info "[DryRun] git push origin main && git push origin $tag"
} else {
    Run { git add -A }
    Run { git commit -m "release: $tag" }
    Run { git tag -a $tag -m "$tag" }
    Run { git push origin main }
    Run { git push origin $tag }
    Info "已提交并推送 $tag"
}

# ---------- 9. 建 GitHub Release ----------
if ($DryRun) {
    Info "[DryRun] gh release create $tag <apk> --title $tag --notes-file <notes> --latest"
} else {
    if (-not (Test-Path $gh)) { Die "找不到 gh CLI（$gh），请安装或修正路径" }
    Info "创建 GitHub Release…"
    Run { & $gh release create $tag $apkDst --title $tag --notes-file $notesPath --latest }
    $releaseUrl = (& $gh release view $tag --repo 'flechazoyang-google/tools' --json url --jq .url).Trim()
    Info "Release：$releaseUrl"
}

# ---------- 10. 触发网站更新 ----------
if ($DryRun) {
    Info "[DryRun] gh workflow run update-projects.yml --repo $websiteRepo"
} else {
    & $gh workflow run update-projects.yml --repo $websiteRepo 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Info "已触发网站数据更新（personal-website）"
    } else {
        Warn "触发网站更新失败（不影响发布）；网站会在 6 小时内自动更新，或手动触发："
        Warn "  gh workflow run update-projects.yml --repo $websiteRepo"
    }
}

Write-Host ""
Info "发布完成 ✔"
Info "  版本：$Version (code $newCode)"
Info "  APK ：releases\$tag\$apkName"
Info "  说明：releases\$tag\RELEASE_NOTE.md"
