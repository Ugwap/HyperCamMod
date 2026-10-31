# HyperCamMod

Toolkit **systemless (Magisk)** untuk memodifikasi tampilan aplikasi kamera bawaan HyperOS
(`com.android.camera` / MiuiCamera) di perangkat sendiri.

Repo ini **tidak menyimpan APK proprietary Xiaomi**. Yang disimpan hanya skrip, template
modul, dan dokumentasi. APK asli diambil dari HP kamu sendiri lewat ADB.

> Status: **workbench / tahap analisis** — menunggu APK dari perangkat untuk dicek
> (sharedUserId, signature check, apakah UI-nya XML atau Compose).

## Peringatan

- Ini mengubah **aplikasi sistem**. Risiko: kamera crash, aplikasi tidak muncul, bahkan
  bootloop jika modul salah pasang.
- Wajib punya **bootloader unlock + Magisk/KernelSU** dan **backup APK asli** (skrip
  `pull-stock.ps1` melakukannya otomatis).
- Modul bisa dinonaktifkan/dihapus dari Magisk kapan saja untuk kembali ke asli
  (lihat [docs/REVERT.md](docs/REVERT.md)).
- Jangan pernah commit APK, resource hasil decode, atau keystore ke repo publik.
- APK hasil mod ditandatangani ulang dengan kunci kita. Kalau kamera memakai
  `sharedUserId` sistem atau pengecekan signature, APK mod **tidak akan jalan** —
  jalur alternatifnya ada di [docs/RENCANA.md](docs/RENCANA.md).

## Cara kerja

```
HP (root) --adb--> stock/MiuiCamera.apk
                         |
                    apktool decode
                         |
              decoded/res/... (edit tampilan)
                         |
               apktool build + zipalign + apksigner
                         |
        build/module/system/product/priv-app/MiuiCamera/MiuiCamera.apk
                         |
                  zip -> flash di Magisk -> reboot
```

Magisk menimpa APK asli secara systemless, jadi partisi sistem tidak berubah permanen.

## Yang dibutuhkan

- Windows + PowerShell
- JDK 17 (`java`, `keytool` di PATH)
- Android SDK Platform Tools (`adb`) + Build Tools (`zipalign`, `apksigner`)
- Magisk v26+ (atau KernelSU dengan dukungan modul Magisk)
- HP: USB debugging aktif, akses root untuk `adb shell su` (opsional, untuk cek path)

`apktool.jar` diunduh otomatis oleh `scripts/decode.ps1`.

## Quick start

```powershell
# 1. Ambil APK + framework MIUI dari HP (butuh HP terhubung)
powershell -ExecutionPolicy Bypass -File scripts\pull-stock.ps1

# 2. Decode APK
powershell -ExecutionPolicy Bypass -File scripts\decode.ps1

# 3. Edit tampilan di decoded\res\... lalu build ulang + sign
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1

# 4. Bungkus jadi modul Magisk
powershell -ExecutionPolicy Bypass -File scripts\make-module.ps1

# 5. Flash build\HyperCamMod-*.zip lewat Magisk -> reboot
```

## Isi repo

| Path | Fungsi |
| --- | --- |
| `scripts/pull-stock.ps1` | Pull APK kamera + `framework-miui-res.apk`, simpan info perangkat |
| `scripts/inspect.ps1` | Cek `sharedUserId`, signature, Compose, layout, native libs |
| `scripts/decode.ps1` | Decode APK dengan apktool (framework MIUI otomatis) |
| `scripts/build-apk.ps1` | Rebuild, zipalign, sign dengan keystore lokal |
| `scripts/make-module.ps1` | Bungkus APK hasil sign jadi modul Magisk sesuai path asli |
| `module/` | Template `module.prop` + `customize.sh` |
| `docs/RENCANA.md` | Analisis kelayakan & jalur alternatif (LSPosed/RRO) |
| `docs/REVERT.md` | Cara balik ke kamera asli |

## Lisensi

MIT — hanya untuk skrip dan dokumentasi. Aplikasi kamera HyperOS tetap milik Xiaomi.
