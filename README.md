# HyperCamMod

Modul **LSPosed** untuk membuat tampilan aplikasi kamera bawaan HyperOS
(`com.android.camera` / MiuiCamera) jadi **full-screen** — bar hitam di atas/bawah
hilang, preview menutup seluruh layar, dan ikon atas/bawah tetap terbaca.

Repo ini **tidak menyimpan APK proprietary Xiaomi**. Yang disimpan hanya source modul,
skrip, template, dan dokumentasi. Tidak perlu re-sign APK kamera, jadi tidak kena
pengecekan signature / `sharedUserId`.

> Status: **jalan** di Poco X7 Pro (`2412DPC0AG` / `rodin`), HyperOS `4.0.0.12.XPTCNXM`
> (V816), Android 17 (SDK 37), kamera **6.4.000250.3**. Versi modul: **1.4.0**.
> Detail & batasan versi ada di [Kompatibilitas](#kompatibilitas).

## Yang dilakukan modul

1. **Preview 4:3 dinamis** — SurfaceView preview 4:3 bisa dibesarkan (`scale = 1 +
   (tinggi layar / tinggi konten - 1) x porsi`) dengan pivot di tengah area konten.
   Pilihan:
   - **Otomatis** (default) — di 0,6x / 1x bar hitam atas-bawah tetap ada; begitu
     preview di-zoom (≥ ~1,2x) preview memenuhi layar dan bar hilang.
   - **Mati** — selalu 4:3 asli dengan bar.
   - **50%** — bar tertutup separuh, selalu.
   - **100%** — penuh layar, selalu.
2. **Scrim gradient yang bisa diatur** — lapisan gelap di area atas (200dp) dan bawah
   (320dp) supaya ikon/shutter tetap kontras. Level: **Mati** (default), **25%**, atau
   **50%**. Tanpa blur/PixelCopy — ringan, tidak membaca frame.
3. **Lapisan hitam bawaan dimatikan** — `top_cover_layout`, `bottom_cover_layout`,
   `left/right_cover_layout`, `mask_cover_layout`, `top_config_background`, dst.
   di-set alpha 0 agar tidak menutupi preview.
4. **Top bar & status bar transparan** supaya preview terlihat menyatu di belakang ikon.

5. **Opsi sembunyikan ikon** aplikasi modul dari home screen. Modul tetap aktif dan
   tetap terdaftar di LSPosed Manager — hanya shortcut-nya yang hilang.

Tidak menyentuh capture, video, atau Pro mode (hanya tampilan preview).

### Cara mode Otomatis tahu level zoom

Modul membaca rasio zoom aktif dari `ZoomManager` kamera (`X5.f`, implementasi
`W5.a`): hook `setZoomRatio(float)` dan `e0(float, int)` (update aksi zoom), lalu
nilai terbaru diambil lewat getter `n0()`. Ambangnya **1,05x**: 0,6x dan 1x dianggap
"belum zoom", 2x ke atas memicu layar penuh. Kalau hook ini gagal (nama class berubah
di ROM lain), mode Otomatis jatuh ke perilaku 4:3 (bar tetap ada) dan alasannya
dicatat di log.


### Trade-off

Semakin besar zoom, semakin banyak viewfinder ter-crop di kiri–kanan dibanding hasil
foto 4:3 (zoom 100% ≈ 40% ter-crop). Hasil foto/video tetap normal (tidak di-crop).
Ini konsekuensi dari mengisi layar 20:9 dengan sensor 4:3 — karena itu zoom dibuat
bisa dimatikan.

## Kompatibilitas

Modul **tidak mengunci versi**: tidak ada pengecekan `versionName`/`versionCode`.
Semua id view dicari saat runtime lewat `Resources.getIdentifier`, jadi ROM yang hanya
bergeser nomor resource tetap aman. Yang menentukan jalan/tidaknya adalah nama-nama
internal di APK kamera.

### Sudah diuji

| Item | Nilai |
| --- | --- |
| Perangkat | Poco X7 Pro (`2412DPC0AG` / `rodin`) |
| ROM | HyperOS `4.0.0.12.XPTCNXM` (`ro.miui.ui.version.name` = `V816`) |
| Android | 17 / SDK 37 |
| Aplikasi kamera | MiuiCamera **6.4.000250.3** (`versionCode 740002503`) |
| Path APK | `/product/priv-app/MiuiCamera/MiuiCamera.apk` |

### Ketergantungan

| Yang dicari | Dipakai untuk | Kalau tidak ketemu |
| --- | --- | --- |
| package `com.android.camera` | target hook | modul diam (scope salah) |
| id `camera_app_root` | menempelkan scrim | scrim tidak dipasang |
| id `preview_surface_layout` + `gl_root_cover` | hitung skala crop preview | zoom dilewati, tampilan asli |
| id `top_config_background` | memudarkan top bar | top bar tetap solid |
| class `com.android.camera.ui.ShapeBackGroundView` method `d()` | top bar transparan (paint) | hanya efek ini hilang — bagian paling rapuh karena nama method di-obfuscate |
| class `X5.f` (ZoomManager) method `setZoomRatio(F)` / `e0(FI)` / `n0()` | tahu level zoom untuk mode Otomatis | mode Otomatis jadi 4:3 (bar selalu ada) |
| `SurfaceView` di dalam `preview_surface_layout` | objek yang di-zoom | zoom dilewati |

Semua kegagalan di atas bersifat **no-op** dan dicatat di log, bukan crash: kamera
tetap jalan seperti asli (paling banter efek full-screen-nya tidak muncul).

### Perkiraan dukungan

| Versi kamera / ROM | Status |
| --- | --- |
| MiuiCamera 6.x — HyperOS 2/3/4 | kemungkinan jalan (generasi yang diuji) |
| MiuiCamera 5.x — HyperOS 1 | belum diuji; crop kemungkinan jalan, top bar bisa tidak |
| MiuiCamera 4.x — MIUI 14 ke bawah | kemungkinan tidak (struktur layout beda generasi) |
| Foldable/tablet yang preview-nya sudah full-screen | scrim tetap dipasang, zoom otomatis dilewati |
| Kamera non-Xiaomi (AOSP/GCam) | tidak didukung (package berbeda) |

`minSdk` modul 29 (Android 10+).

### Cara cek di device lain

```powershell
adb logcat -s LSPosedFramework | Select-String "HyperCamMod"
```

Tiga baris penanda sukses (contoh mode Otomatis + scrim mati):

```
HyperCamMod: aktif di com.android.camera
HyperCamMod: hook ShapeBackGroundView.d() terpasang
HyperCamMod: hook ZoomManager.setZoomRatio terpasang
HyperCamMod: config zoom=3 scrim=0%
```

Saat zoom melewati ambang, muncul salah satu:

```
HyperCamMod: auto zoom aktif (2.0x)
HyperCamMod: auto zoom nonaktif (1.0x)
```

Kalau `hook ShapeBackGroundView.d()` gagal, hanya efek top bar transparan yang hilang.
Kalau `hook ZoomManager.setZoomRatio` gagal, mode Otomatis berperilaku seperti 4:3
(bar selalu ada) — pilih mode 50%/100% kalau tetap ingin full-screen.
Kalau baris `config ...` tidak muncul, pengaturan modul belum terbaca — hook memakai
nilai default. Kalau `scrim 25% dipasang` tidak muncul padahal scrim diset 25%, berarti
id resource di ROM itu berbeda dan daftar id perlu ditambah.

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

### Pengaturan tampilan

Buka aplikasi **HyperCamMod** (atau dari LSPosed Manager) → bagian
**Tampilan preview**:

| Pengaturan | Pilihan | Default |
| --- | --- | --- |
| Zoom preview | Otomatis (bar hilang saat di-zoom) / Mati (4:3 asli) / 50% / 100% | Otomatis |
| Scrim gradient atas-bawah | Mati / 25% / 50% | Mati (tanpa kabut) |

Perubahan disimpan saat itu juga, tapi **berlaku setelah kamera dibuka ulang**
(force stop kamera atau tutup dari recents, lalu buka lagi).

Catatan teknis: pengaturan dibaca proses kamera lewat **ordered broadcast eksplisit**
(`ConfigReceiver`). `XSharedPreferences` tidak dipakai karena di ROM ini LSPosed
mengalihkan SharedPreferences modul ke `/data/misc/apexdata` dan SELinux menolak
penulisannya, sedangkan ContentProvider/bound Service tidak bisa diakses karena
package visibility (`not found`).

### Sembunyikan ikon modul

Buka aplikasi **HyperCamMod** → bagian *Ikon aplikasi* → aktifkan
**Sembunyikan ikon dari home screen**. Ikon langsung hilang dari launcher, tapi modul
tetap jalan karena hook ada di proses kamera.

Catatan:

- Ini tidak menyembunyikan aplikasi dari **LSPosed Manager** atau **Settings → Apps**;
  keduanya tetap menampilkan HyperCamMod.
- Saat ikon disembunyikan, layar modul tetap bisa dibuka dari **LSPosed Manager**
  (tombol buka/setelan di kartu modul). Itu jalan karena `MainActivity` mendaftarkan
  category `de.robv.android.xposed.category.MODULE_SETTINGS`, bukan lewat ikon launcher.
- Cadangan kalau perlu jalan lain:

  ```powershell
  adb shell am start -n com.ugwap.hypercammod/.MainActivity
  ```

- Tombol *Open* di Settings → Apps **tidak muncul** selama ikon disembunyikan (tidak
  ada launcher intent). Pakai LSPosed Manager atau perintah adb di atas.
- Menghapus modul tetap bisa dari LSPosed Manager atau
  `adb uninstall com.ugwap.hypercammod`.

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
- Diuji hanya pada konfigurasi di bagian [Kompatibilitas](#kompatibilitas). Versi
  kamera/ROM lain belum diverifikasi.

## Lisensi

MIT — hanya untuk source modul, skrip, dan dokumentasi. Aplikasi kamera HyperOS tetap
milik Xiaomi.
