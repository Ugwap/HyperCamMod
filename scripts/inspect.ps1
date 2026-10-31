<#
.SYNOPSIS
    Analisis APK kamera HyperOS: sharedUserId, signature check, Compose, layout.

.DESCRIPTION
    Dijalankan setelah scripts\decode.ps1. Membaca decoded\AndroidManifest.xml dan
    isi decoded\ untuk menentukan jalur mod (APK replace vs LSPosed).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\inspect.ps1
#>

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$decoded = Join-Path $root "decoded"
$manifestPath = Join-Path $decoded "AndroidManifest.xml"

if (-not (Test-Path $manifestPath)) {
    throw "decoded\AndroidManifest.xml tidak ada. Jalankan scripts\decode.ps1 dulu."
}

[xml]$manifest = Get-Content -Path $manifestPath -Encoding UTF8
$androidNs = "http://schemas.android.com/apk/res/android"
$app = $manifest.manifest

function Get-AndroidAttr($node, [string]$name) {
    if ($null -eq $node) { return $null }
    return $node.GetAttribute($name, $androidNs)
}

$package = $app.GetAttribute("package")
$sharedUserId = $app.GetAttribute("sharedUserId")
$versionName = Get-AndroidAttr $app "versionName"
$versionCode = Get-AndroidAttr $app "versionCode"

$usesSdk = $app.SelectSingleNode("uses-sdk")
$minSdk = Get-AndroidAttr $usesSdk "minSdkVersion"
$targetSdk = Get-AndroidAttr $usesSdk "targetSdkVersion"

$application = $app.SelectSingleNode("application")
$appLabel = Get-AndroidAttr $application "label"
$debuggable = Get-AndroidAttr $application "debuggable"

$layouts = @(Get-ChildItem -Path $decoded -Recurse -File -Filter "*.xml" -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match "\\res\\layout" })
$drawables = @(Get-ChildItem -Path $decoded -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match "\\res\\drawable" })

$smaliDirs = @(Get-ChildItem -Path $decoded -Directory -Filter "smali*" -ErrorAction SilentlyContinue)
$compose = $false
$signatureHits = @()
$sharedUidHits = @()

foreach ($dir in $smaliDirs) {
    $files = Get-ChildItem -Path $dir.FullName -Recurse -File -Filter "*.smali" -ErrorAction SilentlyContinue
    if (-not $compose) {
        $compose = $null -ne ($files | Where-Object { $_.FullName -match "androidx\\compose" } | Select-Object -First 1)
    }
    $signatureHits += @($files |
        Select-String -Pattern "GET_SIGNATURES|GET_SIGNING_CERTIFICATES|checkSignatures" -List -ErrorAction SilentlyContinue |
        Select-Object -First 10)
    $sharedUidHits += @($files |
        Select-String -Pattern "sharedUserId" -List -ErrorAction SilentlyContinue |
        Select-Object -First 5)
}

$activities = @($application.SelectNodes("activity") | ForEach-Object { Get-AndroidAttr $_ "name" })
$nativeLibs = @(Get-ChildItem -Path (Join-Path $decoded "lib") -Recurse -File -Filter "*.so" -ErrorAction SilentlyContinue |
    ForEach-Object { $_.FullName.Substring((Join-Path $decoded "lib").Length + 1) })

$verdict = "A. Re-sign + Magisk replace (kemungkinan bisa)"
$reasons = @()
if ($sharedUserId) {
    $verdict = "B. LSPosed hook (sharedUserId terdeteksi)"
    $reasons += "android:sharedUserId='$sharedUserId' -> APK re-sign tidak bisa memakai uid sistem."
}
if ($signatureHits.Count -gt 0) {
    $verdict = "B. LSPosed hook (ada indikasi cek signature)"
    $reasons += "Ditemukan kode pengecekan signature di $($signatureHits.Count) file smali."
}
if ($compose) {
    $reasons += "UI terindikasi memakai Jetpack Compose -> layout XML terbatas."
}

Write-Host ""
Write-Host "===== INFORMASI APLIKASI ====="
Write-Host "Package        : $package"
Write-Host "Label          : $appLabel"
Write-Host "Version        : $versionName ($versionCode)"
Write-Host "minSdk/target  : $minSdk / $targetSdk"
Write-Host "sharedUserId   : $(if ($sharedUserId) { $sharedUserId } else { '(tidak ada)' })"
Write-Host "Debuggable     : $(if ($debuggable) { $debuggable } else { 'false' })"
Write-Host "Aktivitas      : $($activities.Count)"
Write-Host "Layout XML     : $($layouts.Count) file"
Write-Host "Drawable       : $($drawables.Count) file"
Write-Host "Native libs    : $($nativeLibs.Count) file"
Write-Host "Compose        : $compose"
Write-Host "Cek signature  : $($signatureHits.Count) file"
Write-Host ""
Write-Host "===== KESIMPULAN ====="
Write-Host $verdict
$reasons | ForEach-Object { Write-Host " - $_" }
Write-Host ""

if ($nativeLibs.Count -gt 0) {
    Write-Host "Native libs (jangan dihapus):"
    $nativeLibs | Select-Object -First 20 | ForEach-Object { Write-Host "  $_" }
    Write-Host ""
}

if ($signatureHits.Count -gt 0) {
    Write-Host "Contoh file dengan cek signature:"
    $signatureHits | ForEach-Object {
        Write-Host ("  {0}:{1}" -f $_.Path.Substring($root.Length + 1), $_.LineNumber)
    }
    Write-Host ""
}

Write-Host "Detail lengkap: docs\RENCANA.md"
