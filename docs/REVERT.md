# Cara kembali ke kamera asli

Modul ini **systemless**: partisi `/system`, `/product`, dll. tidak diubah. Menghapus
modul = kembali ke APK asli.

## Cara 1 — dari aplikasi Magisk (disarankan)

1. Buka **Magisk** → tab **Modules**.
2. Cari **HyperCamMod**.
3. Nonaktifkan toggle-nya, atau tekan **Remove**.
4. Reboot.
5. Buka kamera, pastikan kembali normal.

Kalau kamera sempat crash setelah modul aktif, nonaktifkan dulu modulnya, reboot, baru
hapus.

## Cara 2 — dari recovery (kalau bootloop)

1. Boot ke recovery yang mendukung ADB (TWRP/OrangeFox), atau gunakan `adb` saat
   perangkat boot normal kalau masih bisa.
2. Hapus folder modul:

```sh
adb shell su -c "rm -rf /data/adb/modules/hypercammod"
adb reboot
```

Atau dari recovery:

```sh
adb shell rm -rf /data/adb/modules/hypercammod
```

3. Reboot.

## Cara 3 — aman sebelum flashing

Sebelum memasang modul, buat backup APK asli:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\pull-stock.ps1
```

File tersimpan di `stock/MiuiCamera.apk`. Kalau perlu restore manual (butuh akses tulis
ke partisi sistem):

```sh
adb push stock/MiuiCamera.apk /data/local/tmp/MiuiCamera.apk
adb shell su -c "mount -o rw,remount /product"   # path bisa beda
adb shell su -c "cp /data/local/tmp/MiuiCamera.apk /product/priv-app/MiuiCamera/MiuiCamera.apk"
adb shell su -c "chmod 644 /product/priv-app/MiuiCamera/MiuiCamera.apk"
adb reboot
```

> Restore manual hanya kalau benar-benar perlu. Menghapus modul Magisk sudah cukup.

## Setelah revert

- Hapus cache kamera kalau ada tampilan aneh:
  `adb shell pm clear com.android.camera`
- Cek versi: `adb shell dumpsys package com.android.camera | findstr versionName`
