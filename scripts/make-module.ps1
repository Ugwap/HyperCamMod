<#
.SYNOPSIS
    Bungkus APK hasil sign menjadi modul Magisk sesuai path asli di HP.

.DESCRIPTION
    Membaca stock\info.json (hasil pull-stock.ps1) untuk mengetahui lokasi APK asli,
    lalu menaruh APK di struktur modul yang benar:
      /system/...  -> system/...
      /product/... -> system/product/...
      /vendor/...  -> system/vendor/...
    Output: build\HyperCamMod-<versi>.zip

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\make-module.ps1
#>

[CmdletBinding()]
param(
    [string]$SignedApk = "",
    [string]$ModuleName = "HyperCamMod"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path -Parent $PSScriptRoot
$stockDir = Join-Path $root "stock"
$buildDir = Join-Path $root "build"
$templateDir = Join-Path $root "module"
$infoPath = Join-Path $stockDir "info.json"

if (-not (Test-Path $infoPath)) {
    throw "stock\info.json tidak ada. Jalankan scripts\pull-stock.ps1 dulu."
}
if (-not $SignedApk) {
    $SignedApk = Join-Path $buildDir "MiuiCamera-signed.apk"
}
if (-not (Test-Path $SignedApk)) {
    throw "APK hasil sign tidak ada: $SignedApk. Jalankan scripts\build-apk.ps1 dulu."
}

$info = Get-Content -Path $infoPath -Raw -Encoding UTF8 | ConvertFrom-Json
$remote = [string]$info.apkRemotePath
if (-not $remote.StartsWith("/")) {
    throw "apkRemotePath tidak valid di stock\info.json: $remote"
}

$parts = $remote.TrimStart("/").Split("/")
$fileName = $parts[-1]
if ($fileName -notmatch "\.apk$") {
    throw "Path APK asli aneh: $remote"
}

if ($parts[0] -eq "system") {
    $modRel = $parts -join "/"
} else {
    $modRel = "system/" + ($parts -join "/")
}

$moduleDir = Join-Path $buildDir "module"
if (Test-Path $moduleDir) { Remove-Item -Path $moduleDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $moduleDir | Out-Null

Copy-Item -Path (Join-Path $templateDir "module.prop") -Destination $moduleDir
Copy-Item -Path (Join-Path $templateDir "customize.sh") -Destination $moduleDir

$destApk = Join-Path $moduleDir ($modRel -replace "/", "\")
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destApk) | Out-Null
Copy-Item -Path $SignedApk -Destination $destApk

# Simpan path target di modul supaya customize.sh bisa memvalidasi
Set-Content -Path (Join-Path $moduleDir "target.txt") -Value $remote -Encoding ASCII

$versionLine = Select-String -Path (Join-Path $moduleDir "module.prop") -Pattern "^version=(.+)$"
$version = if ($versionLine) { $versionLine.Matches[0].Groups[1].Value } else { "v0.1.0" }

$zipPath = Join-Path $buildDir ("$ModuleName-$version.zip")
if (Test-Path $zipPath) { Remove-Item -Path $zipPath -Force }
[System.IO.Compression.ZipFile]::CreateFromDirectory($moduleDir, $zipPath)

Write-Host ""
Write-Host "Modul dibuat: $zipPath"
Write-Host "Target APK  : $remote"
Write-Host "APK di modul: $modRel"
Write-Host ""
Write-Host "Cara pasang:"
Write-Host "  1. Salin zip ke HP / flash lewat Magisk app -> Modules -> Install from storage"
Write-Host "  2. Reboot"
Write-Host "  3. Uji kamera. Kalau bermasalah, nonaktifkan modul lalu reboot."
