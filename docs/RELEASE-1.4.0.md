## HyperCamMod v1.4.0

Mode zoom baru: **Otomatis** — preview tetap 4:3 (bar hitam atas/bawah ada) saat
kamera di **0,6x / 1x**, dan bar langsung hilang begitu preview di-zoom (mulai ~1,2x,
mis. 2x). Ini meniru perilaku kamera iPhone.

### Yang baru

- **Zoom preview: Otomatis (default)**
  - 0,6x dan 1x → bar hitam atas/bawah tetap ada, preview paling tajam.
  - Di-zoom (≥ ~1,2x) → preview memenuhi layar, bar hilang.
- Pilihan lama tetap ada: **Mati** (selalu 4:3), **50%**, **100%** (selalu penuh).
- **Scrim** tetap bisa diatur: Mati (default) / 25% / 50%.

### Cara kerjanya

Modul membaca rasio zoom aktif dari `ZoomManager` kamera bawaan
(`X5.f` — hook `setZoomRatio(float)` dan `e0(float,int)`, nilai diambil lewat
getter `n0()`), lalu memakai ambang **1,05x**. Jadi tidak menebak dari ukuran view
atau dari teks tombol zoom.

Kalau hook zoom ini tidak tersedia di ROM lain, mode Otomatis otomatis jatuh ke
tampilan 4:3 (bar tetap ada) dan alasannya dicatat di log — kamera tetap aman.

### Perbaikan lain

- Pengaturan tetap tersimpan lewat ordered broadcast eksplisit (`ConfigReceiver`),
  jadi tidak terpengaruh masalah `XSharedPreferences`/SELinux maupun package
  visibility.
- Scrim tidak lagi dipasang saat diset Mati (tidak ada efek berkabut).

### Catatan

- Perubahan setting berlaku setelah **kamera dibuka ulang** (force stop / tutup dari
  recents).
- Hasil foto/video tetap 4:3 penuh; hanya preview yang berubah.
- Ikon modul tetap bisa disembunyikan; layar modul tetap bisa dibuka dari LSPosed
  Manager.

### Pasang

```powershell
adb install -r HyperCamMod-v1.4.0.apk
```

Lalu di LSPosed Manager: aktifkan modul, scope **Camera** (`com.android.camera`),
force stop kamera, buka lagi.

Cek log:

```powershell
adb logcat -s LSPosedFramework | Select-String "HyperCamMod"
```

Harus muncul `hook ZoomManager.setZoomRatio terpasang` dan `config zoom=3 scrim=0%`.
