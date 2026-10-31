<#
.SYNOPSIS
    Build ulang decoded\ menjadi APK, zipalign, dan sign.

.DESCRIPTION
    Output:
      build\MiuiCamera-unsigned.apk
      build\MiuiCamera-aligned.apk
      build\MiuiCamera-signed.apk

    Keystore lokal dibuat otomatis di keys\hypercammod.keystore
    (password default: android). Jangan commit folder keys\.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1
#>

[CmdletBinding()]
param(
    [string]$KeystorePassword = "android",
    [string]$KeyAlias = "hypercammod"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$decodedDir = Join-Path $root "decoded"
$buildDir = Join-Path $root "build"
$toolsDir = Join-Path $root "tools"
$keysDir = Join-Path $root "keys"
$jarPath = Join-Path $toolsDir "apktool.jar"

if (-not (Test-Path (Join-Path $decodedDir "AndroidManifest.xml"))) {
    throw "decoded\ belum ada. Jalankan scripts\decode.ps1 dulu."
}
if (-not (Test-Path $jarPath)) {
    throw "tools\apktool.jar tidak ada. Jalankan scripts\decode.ps1 dulu."
}

New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
New-Item -ItemType Directory -Force -Path $keysDir | Out-Null

function Resolve-Sdk {
    $sdk = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    ) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $sdk) {
        throw "Android SDK tidak ditemukan. Set ANDROID_HOME."
    }
    return [string]$sdk
}

$sdk = Resolve-Sdk
$buildToolsRoot = Join-Path $sdk "build-tools"
if (-not (Test-Path $buildToolsRoot)) {
    throw "build-tools tidak ada di $buildToolsRoot. Install lewat sdkmanager."
}

$bt = Get-ChildItem -Path $buildToolsRoot -Directory |
    Sort-Object { try { [version]$_.Name } catch { [version]"0.0.0" } } -Descending |
    Where-Object { Test-Path (Join-Path $_.FullName "apksigner.bat") } |
    Select-Object -First 1
if (-not $bt) {
    throw "apksigner tidak ditemukan di build-tools. Install Android SDK Build-Tools."
}

$zipalign = Join-Path $bt.FullName "zipalign.exe"
$apksigner = Join-Path $bt.FullName "apksigner.bat"
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) { throw "java tidak ditemukan. Install JDK 17." }
$java = $javaCmd.Source

$unsigned = Join-Path $buildDir "MiuiCamera-unsigned.apk"
$aligned = Join-Path $buildDir "MiuiCamera-aligned.apk"
$signed = Join-Path $buildDir "MiuiCamera-signed.apk"

Write-Host "Build decoded\ ..."
& $java -jar $jarPath b $decodedDir -o $unsigned
if ($LASTEXITCODE -ne 0) { throw "apktool gagal build. Cek error di atas (resource/smali yang diedit)." }

Write-Host "Zipalign ..."
& $zipalign -p -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign gagal." }

$keystore = Join-Path $keysDir "hypercammod.keystore"
if (-not (Test-Path $keystore)) {
    Write-Host "Membuat keystore baru di keys\hypercammod.keystore ..."
    $keytoolCmd = Get-Command keytool -ErrorAction SilentlyContinue
    if (-not $keytoolCmd) { throw "keytool tidak ditemukan. Install JDK 17." }
    & $keytoolCmd.Source -genkeypair -v `
        -keystore $keystore `
        -storepass $KeystorePassword `
        -keypass $KeystorePassword `
        -alias $KeyAlias `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=HyperCamMod, OU=Personal, O=Ugwap, L=-, S=-, C=ID"
    if ($LASTEXITCODE -ne 0) { throw "keytool gagal membuat keystore." }
}

Write-Host "Sign ..."
& $apksigner sign `
    --ks $keystore `
    --ks-key-alias $KeyAlias `
    --ks-pass "pass:$KeystorePassword" `
    --key-pass "pass:$KeystorePassword" `
    --out $signed `
    $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner gagal." }

& $apksigner verify --print-certs $signed
if ($LASTEXITCODE -ne 0) { throw "Verifikasi signature gagal." }

Write-Host ""
Write-Host "Selesai: build\MiuiCamera-signed.apk"
Write-Host "Lanjut: scripts\make-module.ps1"
