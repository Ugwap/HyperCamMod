<#
.SYNOPSIS
    Ambil APK kamera HyperOS + framework MIUI dari HP lewat ADB.

.DESCRIPTION
    Menyimpan APK asli ke stock\MiuiCamera.apk, framework ke
    stock\framework-miui-res.apk, dan metadata perangkat ke stock\info.json.
    APK ini hanya untuk dipakai lokal (jangan di-commit ke repo).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\pull-stock.ps1
    powershell -ExecutionPolicy Bypass -File scripts\pull-stock.ps1 -Serial SKHIGMLFEA7DVWJJ
#>

[CmdletBinding()]
param(
    [string]$Serial = "",
    [string]$Package = "com.android.camera"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$stockDir = Join-Path $root "stock"
New-Item -ItemType Directory -Force -Path $stockDir | Out-Null

function Resolve-Adb {
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
        "C:\Android\platform-tools\adb.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "adb tidak ditemukan. Install Android SDK Platform Tools atau tambahkan adb ke PATH."
}

$adb = Resolve-Adb
$adbArgs = @()
if ($Serial) { $adbArgs += @("-s", $Serial) }

$devices = & $adb devices
$online = @($devices | Select-String -Pattern "\sdevice$")
if ($online.Count -eq 0) {
    throw "Tidak ada perangkat ADB yang siap. Colok HP, aktifkan USB debugging, izinkan dialog 'Allow USB debugging', lalu jalankan ulang."
}

Write-Host "Perangkat: $($online -join ', ')"

Write-Host "Mencari paket $Package ..."
$pathLines = & $adb @adbArgs shell pm path $Package 2>&1
$apkPaths = @($pathLines | Where-Object { $_ -match "^package:" } | ForEach-Object { $_ -replace "^package:", "" } | ForEach-Object { $_.Trim() })
if ($apkPaths.Count -eq 0) {
    throw "Paket '$Package' tidak ditemukan. Cek nama paket kamera dengan: adb shell pm list packages | findstr camera"
}

$baseApk = $apkPaths | Where-Object { $_ -notmatch "split" } | Select-Object -First 1
if (-not $baseApk) { $baseApk = $apkPaths[0] }

Write-Host "APK asli: $baseApk"
$localApk = Join-Path $stockDir "MiuiCamera.apk"
& $adb @adbArgs pull $baseApk $localApk
if ($LASTEXITCODE -ne 0) { throw "Gagal pull APK." }

if ($apkPaths.Count -gt 1) {
    Write-Host "Split APK terdeteksi ($($apkPaths.Count)). Menyimpan daftar split di info.json."
}

Write-Host "Mengambil framework resource (dibutuhkan apktool) ..."
$frameworkCandidates = @(
    "/system_ext/framework/framework-ext-res/framework-ext-res.apk",
    "/system/framework/framework-miui-res.apk",
    "/system/framework/framework-ext-res.apk",
    "/system_ext/framework/framework-miui-res.apk",
    "/product/framework/framework-miui-res.apk"
)
$frameworkPulled = $false
$frameworkLocalName = ""
foreach ($remote in $frameworkCandidates) {
    $probe = & $adb @adbArgs shell "test -f $remote && echo yes" 2>&1
    if ($probe -match "yes") {
        $frameworkLocalName = Split-Path -Leaf $remote
        $localFramework = Join-Path $stockDir $frameworkLocalName
        & $adb @adbArgs pull $remote $localFramework
        if ($LASTEXITCODE -eq 0) {
            $frameworkPulled = $true
            Write-Host "Framework: $remote"
            break
        }
    }
}
if (-not $frameworkPulled) {
    Write-Warning "Framework resource MIUI tidak ditemukan. Decode mungkin gagal; cari manual (framework-ext-res.apk / framework-miui-res.apk)."
}

Write-Host "Menyimpan metadata ..."
$props = [ordered]@{}
foreach ($prop in @(
    "ro.product.model",
    "ro.product.device",
    "ro.build.version.release",
    "ro.build.version.sdk",
    "ro.miui.ui.version.name",
    "ro.build.version.incremental"
)) {
    $value = (& $adb @adbArgs shell getprop $prop 2>&1) -join ""
    $props[$prop] = $value.Trim()
}

$dumpsys = (& $adb @adbArgs shell dumpsys package $Package 2>&1) -join "`n"
Set-Content -Path (Join-Path $stockDir "package.txt") -Value $dumpsys -Encoding UTF8

$info = [ordered]@{
    package       = $Package
    apkRemotePath = $baseApk
    apkSplits     = @($apkPaths | Where-Object { $_ -ne $baseApk })
    apkLocal      = "stock/MiuiCamera.apk"
    frameworkLocal = $(if ($frameworkLocalName) { "stock/$frameworkLocalName" } else { $null })
    pulledAt      = (Get-Date).ToString("s")
    props         = $props
}
$info | ConvertTo-Json -Depth 5 | Set-Content -Path (Join-Path $stockDir "info.json") -Encoding UTF8

Write-Host ""
Write-Host "Selesai:"
Write-Host "  stock\MiuiCamera.apk"
Write-Host "  stock\info.json"
Write-Host ""
Write-Host "Jangan commit folder stock\. Lanjut: scripts\decode.ps1"
