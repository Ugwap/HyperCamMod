# HyperCamMod

Modul **LSPosed** untuk membuat tampilan aplikasi kamera bawaan HyperOS
(`com.android.camera` / MiuiCamera) jadi **full-screen** â€” bar hitam di atas/bawah
hilang, preview menutup seluruh layar, dan ikon atas/bawah tetap terbaca.

Repo ini **tidak menyimpan APK proprietary Xiaomi**. Yang disimpan hanya source modul,
skrip, template, dan dokumentasi. Tidak perlu re-sign APK kamera, jadi tidak kena
pengecekan signature / `sharedUserId`.

> Status: **jalan** di Poco X7 Pro (`2412DPC0AG` / `rodin`), HyperOS `4.0.0.12.XPTCNXM`
> (V816), Android 17 (SDK 37), kamera **6.7.000290.4**. Versi modul: **1.6.6**.
> Detail & batasan versi ada di [Kompatibilitas](#kompatibilitas).

## Yang dilakukan modul

1. **Preview 4:3 dinamis** â€” SurfaceView preview 4:3 bisa dibesarkan (`scale = 1 +
   (tinggi layar / tinggi konten - 1) x porsi`) dengan pivot di tengah area konten.
   Pilihan:
   - **Otomatis** (default) â€” di 0,6x / 1x bar hitam atas-bawah tetap ada; begitu
     preview di-zoom (â‰¥ ~1,2x) preview memenuhi layar dan bar hilang. Perpindahannya
     **dianimasikan ~220 ms** (decelerate) supaya tidak terlihat "patah", dan ada
     histeresis: kembali ke 4:3 saat zoom turun di bawah **1,02x** agar tidak berkedip
     di batas ambang.
   - **Mati** â€” selalu 4:3 asli dengan bar.
   - **50%** â€” bar tertutup separuh, selalu.
   - **100%** â€” penuh layar, selalu.
2. **Scrim gradient yang bisa diatur** â€” lapisan gelap di area atas (200dp) dan bawah
   (320dp) supaya ikon/shutter tetap kontras. Level: **Mati** (default), **25%**, atau
   **50%**. Tanpa blur/PixelCopy â€” ringan, tidak membaca frame.
3. **Sudut preview membulat** â€” `SurfaceView.setCornerRadius` milik sistem dipakai
   untuk membulatkan sudut preview (sekaligus lubang view-nya), jadi tetap rapi saat
   preview di-scale. Pilihan **Mati** (default), **20 dp**, **40 dp**.
4. **Lapisan hitam bawaan dimatikan** â€” `top_cover_layout`, `bottom_cover_layout`,
   `left/right_cover_layout`, `mask_cover_layout`, `top_config_background`, dst.
   di-set alpha 0 agar tidak menutupi preview.
5. **Top bar & status bar transparan** supaya preview terlihat menyatu di belakang ikon.

6. **Opsi sembunyikan ikon** aplikasi modul dari home screen. Modul tetap aktif dan
   tetap terdaftar di LSPosed Manager â€” hanya shortcut-nya yang hilang.
7. **Transisi zoom lebih halus** - animasi zoom bawaan kamera (tombol 0,6x/1x/2x dan
   snap slider) memakai `LinearInterpolator` dengan durasi yang hampir sama dengan
   selisih zoom-nya, bahkan 0 ms di beberapa kondisi, jadi terasa melompat. Modul
   memperpanjang animator itu, mengganti kurvanya, lalu **menyaring nilai rasio per
   frame dengan exponential smoothing** supaya tidak ada hentakan di awal maupun di
   akhir: **Bawaan** / **Halus** (default) / **Santai**.

Tidak menyentuh capture, video, atau Pro mode (hanya tampilan preview).

### Cara transisi zoom diperhalus

Kamera membuat animator zoom lewat pabrik animator di dalam APK. Nama class-nya
berubah tiap versi kamera, jadi modul mencoba beberapa titik hook:

| Versi kamera | Target hook | Dipakai oleh |
| --- | --- | --- |
| 6.7+ | `Vr.j.m(float, float)` / `Vr.j.n(float, float)` | `I4.W` (toggle zoom) dan `I4.w` (panel zoom) |
| 6.7+ | `ValueAnimator.start()` | merapikan animator yang sudah ditandai pabrik di atas |
| 6.7+ | `k9.j.setZoomRatio(float)` | perataan rasio per frame (smoothing) |
| 6.4 | `Zb.g.i(float, float)` | `FragmentZoomToggle`, `FragmentZoomPanel`, `FragmentDualMenu` |
| 6.4 | `FragmentZoomToggle.Yh(float, float)` | jalur animasi cadangan |
| 6.4 | `X5.f.setZoomRatio(float)` | perataan rasio per frame (smoothing) |

Durasi asli animator dibaca (`getDuration()`) setelah kamera memasangnya sendiri
(0/100/150 ms di kamera 6.7), lalu diskalakan:

- **Halus** (default) - `durasi x 4,5`, dibatasi 440-880 ms, `PathInterpolator(0.4, 0, 0.2, 1)`
  (mulai pelan, cepat di tengah, melambat di akhir)
- **Santai** - `durasi x 7,0`, dibatasi 750-1400 ms, kurva sama
- **Bawaan** - tidak diubah sama sekali

Memperpanjang animator saja masih menyisakan hentakan, karena kurva gabungan
interpolator kamera + spline pabrik tetap punya perubahan kecepatan yang tajam.
Karena itu modul mengambil **rasio tujuan** dari animator (argumen kedua pabrik
`Vr.j.m` / `Vr.j.n`, atau `Zb.g.i` di 6.4), lalu menjalankan peralihannya sendiri:

```text
posisi = awal + (target - awal) x (1 - e^(-3t)) / (1 - e^(-3))
t      = waktu berjalan / durasi animator
```

- titik awal diambil dari rasio zoom yang benar-benar aktif (`ZoomManager.j1()` /
  `n0()`), bukan dari frame pertama animator
- frame yang dikirim kamera **tidak dipakai sebagai target** - kamera 6.7 kadang
  mengirim frame pertama yang sedikit mundur dari posisi sekarang, dan itu yang
  dulu terlihat sebagai "zoom out kecil" sesaat setelah menekan tombol zoom
- kurvanya sama dengan filter eksponensial lama (cepat di awal, melambat di
  akhir), tapi dinormalisasi supaya **tepat mendarat di target saat t = 1**;
  tanpa normalisasi masih tersisa 2-5% jarak
- saat animator kamera berakhir, posisi dipaksa tepat di target dan frame
  susulan tidak boleh menariknya mundur. Ini yang menghilangkan zoom-out kecil
  **setelah** gerakan sampai di target: kamera sempat meng-commit target penuh
  di akhir animasinya, jadi rasio modul harus sudah di titik yang sama
- tick lanjutan tiap 16 ms menjaga gerakan tetap halus sampai animator kamera
  benar-benar berhenti (±80 ms setelah durasi ramp)
- filter hanya aktif ±2,5 detik setelah animator zoom kamera dibuat, dan langsung
  berhenti kalau pengguna memutar zoom melewati target (pinch)

Karena animator zoom kamera tetap yang dipakai (bukan animator baru buatan modul),
setiap frame tetap memanggil jalur zoom resmi kamera. Kalau class target tidak ada di
ROM lain, hook gagal dengan catatan di log dan zoom kembali ke perilaku bawaan.

### Cara mode Otomatis tahu level zoom

Modul membaca rasio zoom aktif dari `ZoomManager` kamera:

| Versi kamera | Class | Method yang di-hook |
| --- | --- | --- |
| 6.7+ | `k9.j` | `setZoomRatio(float)`, `d4(float, int)`, `qc(float, int)` |
| 6.4 | `X5.f` (implementasi `W5.a`) | `setZoomRatio(float)`, `e0(float, int)` |

Ambangnya **1,05x**: 0,6x dan 1x dianggap
"belum zoom", 2x ke atas memicu layar penuh. Transisi skala dianimasikan 220 ms
dengan `ValueAnimator` + `DecelerateInterpolator`; animasi baru dimulai saat ambang
benar-benar terlewati (target berubah), jadi tidak diulang oleh layout listener.
Saat turun, tampilan baru kembali 4:3 di bawah 1,02x (histeresis). Kalau hook ini
gagal (nama class berubah di ROM lain), mode Otomatis jatuh ke perilaku 4:3 (bar
tetap ada) dan alasannya dicatat di log.


### Trade-off

Semakin besar zoom, semakin banyak viewfinder ter-crop di kiriâ€“kanan dibanding hasil
foto 4:3 (zoom 100% â‰ˆ 40% ter-crop). Hasil foto/video tetap normal (tidak di-crop).
Ini konsekuensi dari mengisi layar 20:9 dengan sensor 4:3 â€” karena itu zoom dibuat
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
| Aplikasi kamera | MiuiCamera **6.7.000290.4** (`versionCode 670002904`) |
| Path APK | `/data/app/.../com.android.camera-.../base.apk` |

### Ketergantungan

| Yang dicari | Dipakai untuk | Kalau tidak ketemu |
| --- | --- | --- |
| package `com.android.camera` | target hook | modul diam (scope salah) |
| id `camera_app_root` | menempelkan scrim | scrim tidak dipasang |
| id `preview_surface_layout` + `gl_root_cover` | hitung skala crop preview | zoom dilewati, tampilan asli |
| id `top_config_background` | memudarkan top bar | top bar tetap solid |
| class `ShapeBackGroundView` method penyiapan: `com.xiaomi.camera.ui.base.halo.ShapeBackGroundView.b()` (6.7+) atau `com.android.camera.ui.ShapeBackGroundView.d()` (6.4) | top bar transparan (paint) | hanya efek ini hilang - bagian paling rapuh karena nama method di-obfuscate |
| ZoomManager: `k9.j` `setZoomRatio(F)` / `d4(FI)` / `qc(FI)` (6.7+) atau `X5.f` `setZoomRatio(F)` / `e0(FI)` (6.4) | tahu level zoom untuk mode Otomatis | mode Otomatis jadi 4:3 (bar selalu ada) |
| `SurfaceView` di dalam `preview_surface_layout` | objek yang di-zoom | zoom dilewati |
| `SurfaceView.setCornerRadius(float)` (hidden API) | sudut preview membulat | sudut tetap kotak, dicatat di log |

Semua kegagalan di atas bersifat **no-op** dan dicatat di log, bukan crash: kamera
tetap jalan seperti asli (paling banter efek full-screen-nya tidak muncul).

### Perkiraan dukungan

| Versi kamera / ROM | Status |
| --- | --- |
| MiuiCamera 6.x â€” HyperOS 2/3/4 | kemungkinan jalan (generasi yang diuji) |
| MiuiCamera 5.x â€” HyperOS 1 | belum diuji; crop kemungkinan jalan, top bar bisa tidak |
| MiuiCamera 4.x â€” MIUI 14 ke bawah | kemungkinan tidak (struktur layout beda generasi) |
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
HyperCamMod: config zoom=3 scrim=0% sudut=0dp
```

Saat zoom melewati ambang, muncul salah satu:

```
HyperCamMod: auto zoom aktif (2.0x)
HyperCamMod: auto zoom nonaktif (1.0x)
```

Kalau opsi sudut diaktifkan, muncul `sudut preview = Npx`. Kalau baris itu berisi
`sudut preview gagal (setCornerRadius)`, berarti ROM tidak menyediakan API tersebut
dan sudut tetap kotak (tidak ada efek samping lain).

Kalau `hook ShapeBackGroundView.d()` gagal, hanya efek top bar transparan yang hilang.
Kalau `hook ZoomManager.setZoomRatio` gagal, mode Otomatis berperilaku seperti 4:3
(bar selalu ada) â€” pilih mode 50%/100% kalau tetap ingin full-screen.
Kalau baris `config ...` tidak muncul, pengaturan modul belum terbaca â€” hook memakai
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

`local.properties` (path SDK) tidak ikut di-commit â€” buat sendiri kalau perlu:

```
sdk.dir=C\:\\Users\\<nama>\\AppData\\Local\\Android\\Sdk
```

### Pengaturan tampilan

Buka aplikasi **HyperCamMod** (atau dari LSPosed Manager) â†’ bagian
**Tampilan preview**:

| Pengaturan | Pilihan | Default |
| --- | --- | --- |
| Zoom preview | Otomatis (bar hilang saat di-zoom) / Mati (4:3 asli) / 50% / 100% | Otomatis |
| Scrim gradient atas-bawah | Mati / 25% / 50% | Mati (tanpa kabut) |
| Sudut preview | Mati / 20 dp / 40 dp | Mati (kotak) |
| Transisi zoom | Bawaan / Halus / Santai | Halus |

Perubahan disimpan saat itu juga, tapi **berlaku setelah kamera dibuka ulang**
(force stop kamera atau tutup dari recents, lalu buka lagi).

Catatan teknis: pengaturan dibaca proses kamera lewat **ordered broadcast eksplisit**
(`ConfigReceiver`). `XSharedPreferences` tidak dipakai karena di ROM ini LSPosed
mengalihkan SharedPreferences modul ke `/data/misc/apexdata` dan SELinux menolak
penulisannya, sedangkan ContentProvider/bound Service tidak bisa diakses karena
package visibility (`not found`).

### Sembunyikan ikon modul

Buka aplikasi **HyperCamMod** â†’ bagian *Ikon aplikasi* â†’ aktifkan
**Sembunyikan ikon dari home screen**. Ikon langsung hilang dari launcher, tapi modul
tetap jalan karena hook ada di proses kamera.

Catatan:

- Ini tidak menyembunyikan aplikasi dari **LSPosed Manager** atau **Settings â†’ Apps**;
  keduanya tetap menampilkan HyperCamMod.
- Saat ikon disembunyikan, layar modul tetap bisa dibuka dari **LSPosed Manager**
  (tombol buka/setelan di kartu modul). Itu jalan karena `MainActivity` mendaftarkan
  category `de.robv.android.xposed.category.MODULE_SETTINGS`, bukan lewat ikon launcher.
- Cadangan kalau perlu jalan lain:

  ```powershell
  adb shell am start -n com.ugwap.hypercammod/.MainActivity
  ```

- Tombol *Open* di Settings â†’ Apps **tidak muncul** selama ikon disembunyikan (tidak
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
akan jalan** â€” itulah kenapa jalur LSPosed yang dipakai.

## Peringatan

- Ini mengubah tampilan **aplikasi sistem**. Risiko: kamera crash atau modul bentrok
  dengan update HyperOS. Selalu punya cara disable modul dari LSPosed Manager.
- Jangan commit APK, resource hasil decode, atau keystore ke repo publik.
- Setiap update HyperOS, id resource bisa berubah. `HookEntry.java` membaca id lewat
  `Resources.getIdentifier`, jadi tidak hardcode â€” tapi kalau struktur view berubah,
  perlu penyesuaian.
- Diuji hanya pada konfigurasi di bagian [Kompatibilitas](#kompatibilitas). Versi
  kamera/ROM lain belum diverifikasi.

## Lisensi

MIT â€” hanya untuk source modul, skrip, dan dokumentasi. Aplikasi kamera HyperOS tetap
milik Xiaomi.

