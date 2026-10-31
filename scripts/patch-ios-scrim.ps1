<#
.SYNOPSIS
    Patch tampilan kamera HyperOS: scrim gradient atas/bawah ala iOS.

.DESCRIPTION
    Dijalankan setelah scripts\decode.ps1. Perubahan:
      1. Tambah drawable hcm_scrim_top / hcm_scrim_bottom (gradient hitam).
      2. Sisipkan dua View gradient di layout utama (esc.xml & ese.xml).
      3. Buat background top bar (ShapeBackGroundView id=top_config_background)
         transparan supaya preview terlihat di belakangnya.

    Patch smali hanya mengubah warna background top bar, bukan logika kamera.
    Idempotent: aman dijalankan berulang.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\patch-ios-scrim.ps1
#>

[CmdletBinding()]
param(
    [string]$Root = ""
)

$ErrorActionPreference = "Stop"

if (-not $Root) { $Root = Split-Path -Parent $PSScriptRoot }
$decoded = Join-Path $Root "decoded"
$publicPath = Join-Path $decoded "res\values\public.xml"

if (-not (Test-Path (Join-Path $decoded "AndroidManifest.xml"))) {
    throw "decoded\ belum ada. Jalankan scripts\decode.ps1 dulu."
}
if (-not (Test-Path $publicPath)) {
    throw "decoded\res\values\public.xml tidak ada."
}

function Write-Utf8NoBom([string]$path, [string]$text) {
    $enc = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $text, $enc)
}

# ---------- 1. Drawable gradient ----------
$drawableDir = Join-Path $decoded "res\drawable"
New-Item -ItemType Directory -Force -Path $drawableDir | Out-Null

$topScrim = @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="270"
        android:endColor="#00000000"
        android:startColor="#80000000" />
</shape>
'@

$bottomScrim = @'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="90"
        android:endColor="#00000000"
        android:startColor="#80000000" />
</shape>
'@

Write-Utf8NoBom (Join-Path $drawableDir "hcm_scrim_top.xml") ($topScrim -replace "`r`n", "`n")
Write-Utf8NoBom (Join-Path $drawableDir "hcm_scrim_bottom.xml") ($bottomScrim -replace "`r`n", "`n")
Write-Host "OK  drawable scrim"

# ---------- 2. Sisipkan View ke layout utama ----------
$scrimViews = @'
    <View
        android:layout_width="fill_parent"
        android:layout_height="200dp"
        android:layout_gravity="top"
        android:background="@drawable/hcm_scrim_top" />
    <View
        android:layout_width="fill_parent"
        android:layout_height="320dp"
        android:layout_gravity="bottom"
        android:background="@drawable/hcm_scrim_bottom" />
'@ -replace "`r`n", "`n"

foreach ($name in @("esc.xml", "ese.xml")) {
    $path = Join-Path $decoded "res\layout\$name"
    if (-not (Test-Path $path)) {
        Write-Warning "$name tidak ditemukan, dilewati."
        continue
    }
    $text = [System.IO.File]::ReadAllText($path)
    if ($text.Contains("hcm_scrim_top")) {
        Write-Host "OK  $name sudah dipatch"
        continue
    }
    $anchor = '    <FrameLayout android:id="@id/module_content_lower_layer"'
    if (-not $text.Contains($anchor)) {
        Write-Warning "$name: anchor tidak ditemukan, dilewati."
        continue
    }
    $text = $text.Replace($anchor, $scrimViews + $anchor)
    Write-Utf8NoBom $path $text
    Write-Host "OK  $name"
}

# ---------- 3. Top bar transparan ----------
[xml]$public = Get-Content -Path $publicPath -Encoding UTF8
$idNode = $public.resources.public | Where-Object { $_.type -eq "id" -and $_.name -eq "top_config_background" } | Select-Object -First 1
if (-not $idNode) {
    throw "id top_config_background tidak ditemukan di public.xml."
}
$idHex = [string]$idNode.id
Write-Host "top_config_background = $idHex"

$smali = Get-ChildItem -Path $decoded -Recurse -File -Filter "ShapeBackGroundView.smali" | Select-Object -First 1
if (-not $smali) {
    throw "ShapeBackGroundView.smali tidak ditemukan."
}
$smaliText = [System.IO.File]::ReadAllText($smali.FullName)
if ($smaliText.Contains("cond_hcm_transparent")) {
    Write-Host "OK  smali sudah dipatch"
} else {
    $old = @'
    invoke-virtual {v1, v2, v3}, LZ/d;->a(IZ)I

    move-result v1

    invoke-virtual {p0, v0}, Landroid/view/View;->setWillNotDraw(Z)V
'@ -replace "`r`n", "`n"

    $new = @"
    invoke-virtual {v1, v2, v3}, LZ/d;->a(IZ)I

    move-result v1

    invoke-virtual {p0}, Landroid/view/View;->getId()I

    move-result v3

    const v2, $idHex

    if-ne v3, v2, :cond_hcm_transparent

    const/4 v1, 0x0

    :cond_hcm_transparent
    invoke-virtual {p0, v0}, Landroid/view/View;->setWillNotDraw(Z)V
"@ -replace "`r`n", "`n"

    if (-not $smaliText.Contains($old)) {
        throw "Pola smali tidak ditemukan. Versi APK mungkin berbeda; patch manual diperlukan."
    }
    $smaliText = $smaliText.Replace($old, $new)
    Write-Utf8NoBom $smali.FullName $smaliText
    Write-Host "OK  smali top bar transparan"
}

Write-Host ""
Write-Host "Selesai. Lanjut: scripts\build-apk.ps1 lalu scripts\make-module.ps1"
