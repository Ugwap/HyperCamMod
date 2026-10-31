<#
.SYNOPSIS
    Decode APK kamera HyperOS dengan apktool (framework MIUI otomatis).

.DESCRIPTION
    Mengunduh apktool jika belum ada, memasang framework-miui-res.apk,
    lalu men-decode stock\MiuiCamera.apk ke folder decoded\.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\decode.ps1
    powershell -ExecutionPolicy Bypass -File scripts\decode.ps1 -Force
#>

[CmdletBinding()]
param(
    [string]$ApktoolVersion = "2.11.1",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$stockDir = Join-Path $root "stock"
$toolsDir = Join-Path $root "tools"
$decodedDir = Join-Path $root "decoded"
$apkPath = Join-Path $stockDir "MiuiCamera.apk"
$jarPath = Join-Path $toolsDir "apktool.jar"

if (-not (Test-Path $apkPath)) {
    throw "stock\MiuiCamera.apk tidak ada. Jalankan scripts\pull-stock.ps1 dulu."
}

New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null

if (-not (Test-Path $jarPath)) {
    $url = "https://github.com/iBotPeaches/Apktool/releases/download/v$ApktoolVersion/apktool_$ApktoolVersion.jar"
    Write-Host "Mengunduh apktool $ApktoolVersion ..."
    Invoke-WebRequest -Uri $url -OutFile $jarPath -UseBasicParsing
}

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    throw "java tidak ditemukan. Install JDK 17 dan pastikan ada di PATH."
}
$java = $javaCmd.Source

$framework = Join-Path $stockDir "framework-miui-res.apk"
if (Test-Path $framework) {
    Write-Host "Memasang framework MIUI ..."
    & java -jar $jarPath if $framework
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Gagal memasang framework MIUI. Decode mungkin error; coba manual: java -jar tools\apktool.jar if stock\framework-miui-res.apk"
    }
} else {
    Write-Warning "framework-miui-res.apk tidak ada. Decode mungkin gagal."
}

if ((Test-Path $decodedDir) -and -not $Force) {
    throw "Folder decoded\ sudah ada. Pakai -Force untuk menimpa (perubahan yang belum di-build akan hilang)."
}
if (Test-Path $decodedDir) {
    Remove-Item -Path $decodedDir -Recurse -Force
}

Write-Host "Decode $apkPath ..."
& java -jar $jarPath d -f -o $decodedDir $apkPath
if ($LASTEXITCODE -ne 0) {
    throw "apktool gagal decode. Cek pesan di atas (biasanya framework MIUI belum terpasang)."
}

Write-Host ""
Write-Host "Selesai: decoded\"
Write-Host "Lanjut: scripts\inspect.ps1, lalu edit tampilan di decoded\res\"
