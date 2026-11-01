## HyperCamMod v1.6.0

Perubahan utama: **perpindahan zoom kamera bisa dibuat lebih halus**. Cocok kalau
tombol 0,6x / 1x / 2x terasa melompat, terutama saat pindah antar lensa.

### Yang berubah

- **Pengaturan baru "Transisi zoom"** di aplikasi modul:
  - **Bawaan** - animasi kamera apa adanya (paling cepat, tanpa perubahan).
  - **Halus** (default) - durasi animasi dikali ~2,2 (220-460 ms) dan kurvanya
    diganti `DecelerateInterpolator`, jadi perpindahan melambat di akhir.
  - **Santai** - durasi dikali ~3,4 (320-700 ms), untuk yang ingin paling pelan.
- Animasi zoom bawaan kamera memakai `LinearInterpolator` dengan durasi hampir
  sama dengan selisih zoom-nya - 1x ke 2x hanya ~100 ms, dan 0 ms di beberapa
  jalur. Itu sebabnya terasa "patah".
- Modul mengambil alih animator yang sama, bukan membuat animasi baru:
  - `Zb.g.i(float, float)` - dipakai `FragmentZoomToggle`, `FragmentZoomPanel`,
    `FragmentDualMenu`.
  - `FragmentZoomToggle.Yh(float, float)` - jalur animasi cadangan.
  Karena animator asli tetap dipakai, setiap frame tetap memanggil jalur zoom
  resmi kamera, jadi hasil foto/video tidak berubah.
- Log baru:
  - `HyperCamMod: config transisiZoom=1`
  - `HyperCamMod: animasi zoom 100ms -> 220ms`

### Tetap seperti v1.5.1

- Zoom preview: Otomatis (default) / Mati / 50% / 100%, transisi bar 220 ms.
- Scrim gradient Mati / 25% / 50%, sudut preview Mati / 20 dp / 40 dp.
- Tanpa latar hitam tambahan saat zoom, tanpa blur/PixelCopy.

### Pasang

```powershell
adb install -r HyperCamMod-v1.6.0.apk
```

Lalu **force stop** aplikasi Kamera (atau tutup dari recents) supaya kode modul
baru dimuat, lalu buka lagi. Scope tidak perlu diubah: `com.android.camera`.

### Cara membandingkan

1. Buka aplikasi HyperCamMod → **Tampilan preview** → **Transisi zoom**.
2. Pilih **Bawaan**, buka ulang kamera, ketuk 1x lalu 2x - perhatikan kecepatannya.
3. Ganti ke **Halus**, buka ulang kamera, ulangi. Kalau masih terasa cepat,
   pilih **Santai**.

Kalau tidak ada perubahan sama sekali, cek log:

```powershell
adb logcat -s LSPosedFramework | Select-String "HyperCamMod"
```

Harus muncul `hook animasi zoom (Zb.g.i) terpasang`. Kalau yang muncul
`hook animasi zoom Zb.g.i gagal`, nama class di ROM itu berbeda - kirim baris
log-nya.
