# HyperCamMod

Modul **LSPosed** untuk membuat tampilan aplikasi kamera bawaan HyperOS
(`com.android.camera` / MiuiCamera) jadi **full-screen** — bar hitam di atas/bawah
hilang, preview menutup seluruh layar, dan ikon atas/bawah tetap terbaca.

Repo ini **tidak menyimpan APK proprietary Xiaomi**. Yang disimpan hanya source modul,
skrip, template, dan dokumentasi. Tidak perlu re-sign APK kamera, jadi tidak kena
pengecekan signature / `sharedUserId`.

> Status: **jalan** di Poco X7 Pro (`2412DPC0AG` / `rodin`), HyperOS V816,
> Android 17 (SDK 37). Versi modul: **1.1.0**.

## Yang dilakukan modul

1. **Preview center-crop** — SurfaceView preview 4:3 diperbesar (`scale = tinggi layar /
   tinggi konten`) dengan pivot di tengah area konten, sehingga bar hitam atas/bawah
   hilang dan preview menutup layar.
2. **Scrim gradient 50%** (`#80000000`) di area atas (200dp) dan bawah (320dp) supaya
   ikon/shutter tetap kontras. Tanpa blur/PixelCopy — ringan, tidak membaca frame.
3. **Lapisan hitam bawaan dimatikan** — `top_cover_layout`, `bottom_cover_layout`,
   `left/right_cover_layout`, `mask_cover_layout`, `top_config_background`, dst.
   di-set alpha 0 agar tidak menutupi preview.
4. **Top bar & status bar transparan** supaya preview terlihat menyatu di belakang ikon.

Tidak menyentuh capture, video, zoom, atau Pro mode.

### Trade-off

Karena memilih **preview full-screen (crop)**, viewfinder ter-crop sekitar 40% di
kiri–kanan dibanding hasil foto 4:3. Hasil foto/video tetap normal (tidak di-crop).
Ini konsekuensi dari mengisi layar 20:9 dengan sensor 4:3.

## Cara build & pasang

Prasyarat: JDK 17, Android SDK (compileSdk 35), HP dengan **LSPosed** aktif.

```powershell
cd lsposed
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Lalu di **LSPosed Manager**:

1. Aktifkan modul **HyperCamMod**.
2. Scope: centang **Camera** (`com.android.camera`).
3. Force stop kamera (atau reboot), buka kamera lagi.

Cek log kalau tidak berubah:

```powershell
adb logcat -s LSPosedFramework | Select-String "HyperCamMod"
```

`local.properties` (path SDK) tidak ikut di-commit — buat sendiri kalau perlu:

```
sdk.dir=C\:\\Users\\<nama>\\AppData\\Local\\Android\\Sdk
```

## Isi repo

| Path | Fungsi |
| --- | --- |
| `lsposed/` | Source modul LSPosed (Java, hook `HookEntry.java`) |
| `lsposed/app/libs/api-82.jar` | API Xposed (`compileOnly`) |
| `scripts/pull-stock.ps1` | Pull APK kamera + `framework-miui-res.apk` dari HP |
| `scripts/inspect.ps1` | Cek `sharedUserId`, signature, Compose, layout, native libs |
| `scripts/decode.ps1` | Decode APK dengan apktool (framework MIUI otomatis) |
| `scripts/build-apk.ps1` | Rebuild, zipalign, sign dengan keystore lokal |
| `scripts/make-module.ps1` | Bungkus APK hasil sign jadi modul Magisk |
| `scripts/patch-ios-scrim.ps1` | Patch scrim jalur re-sign (cadangan) |
| `module/` | Template `module.prop` + `customize.sh` untuk jalur Magisk |
| `docs/PATCH.md` | Detail patch scrim jalur re-sign |
| `docs/RENCANA.md` | Analisis kelayakan & jalur alternatif |
| `docs/REVERT.md` | Cara balik ke kamera asli |

## Jalur cadangan: re-sign APK + modul Magisk

Dipakai hanya kalau LSPosed tidak tersedia. Alurnya:

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

```powershell
powershell -ExecutionPolicy Bypass -File scripts\pull-stock.ps1
powershell -ExecutionPolicy Bypass -File scripts\decode.ps1
powershell -ExecutionPolicy Bypass -File scripts\patch-ios-scrim.ps1
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1
powershell -ExecutionPolicy Bypass -File scripts\make-module.ps1
```

Modul Magisk bisa dinonaktifkan/dihapus kapan saja (lihat [docs/REVERT.md](docs/REVERT.md)).
Kalau kamera memakai `sharedUserId` sistem atau pengecekan signature, APK mod **tidak
akan jalan** — itulah kenapa jalur LSPosed yang dipakai.

## Peringatan

- Ini mengubah tampilan **aplikasi sistem**. Risiko: kamera crash atau modul bentrok
  dengan update HyperOS. Selalu punya cara disable modul dari LSPosed Manager.
- Jangan commit APK, resource hasil decode, atau keystore ke repo publik.
- Setiap update HyperOS, id resource bisa berubah. `HookEntry.java` membaca id lewat
  `Resources.getIdentifier`, jadi tidak hardcode — tapi kalau struktur view berubah,
  perlu penyesuaian.
- Diuji hanya di device/ROM di atas.

## Lisensi

MIT — hanya untuk source modul, skrip, dan dokumentasi. Aplikasi kamera HyperOS tetap
milik Xiaomi.
