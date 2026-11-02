## HyperCamMod v1.6.1

Perbaikan untuk **kamera HyperOS versi baru (6.7.x)**. Setelah aplikasi Kamera
di-update, semua hook v1.6.0 gagal karena nama class di dalam APK kamera berubah,
sehingga transisi zoom kembali melompat dan mode Otomatis tidak jalan.

### Yang berubah

Titik hook baru untuk kamera 6.7+ (tetap kompatibel dengan 6.4):

| Fungsi | Kamera 6.7+ | Kamera 6.4 (lama) |
| --- | --- | --- |
| Animasi zoom | `Vr.j.m(float, float)` dan `Vr.j.n(float, float)`, dirapikan di `ValueAnimator.start()` | `Zb.g.i(float, float)` |
| ZoomManager (mode Otomatis) | `k9.j` - `setZoomRatio(float)`, `d4(float, int)`, `qc(float, int)`, getter `j1()` | `X5.f` - `setZoomRatio(float)`, `e0(float, int)`, getter `n0()` |
| Top bar transparan | `com.xiaomi.camera.ui.base.halo.ShapeBackGroundView.b()` | `com.android.camera.ui.ShapeBackGroundView.d()` |

Cara kerja transisi zoom tetap sama: animator zoom milik kamera sendiri yang
diperpanjang dan kurvanya diganti `DecelerateInterpolator`, bukan animasi baru.
Kamera 6.7 memasang durasi 0/100/150 ms setelah animator dibuat, jadi modul
menandai animator dari pabriknya lalu merapikannya tepat sebelum `start()`.

### Hasil uji langsung (adb, Poco X7 Pro)

Kamera **6.7.000290.4**, mode **Halus** (default):

```
animasi zoom 99ms  -> 220ms
animasi zoom 171ms -> 376ms
animasi zoom 73ms  -> 220ms
```

Mode **Santai**: 99 ms -> 337 ms, 171 ms -> 581 ms, 73 ms -> 320 ms.
Mode **Bawaan**: tidak ada perubahan durasi sama sekali.

Mode Otomatis juga sudah dicek: di 1x preview tetap 4:3 (bar atas-bawah ada),
di 2x preview memenuhi layar, kembali ke 1x bar muncul lagi.

### Log yang benar

```
HyperCamMod: hook ShapeBackGroundView.b() terpasang
HyperCamMod: hook ZoomManager (k9.j) terpasang
HyperCamMod: hook animasi zoom (Vr.j.m) terpasang
HyperCamMod: hook animasi zoom (Vr.j.n) terpasang
HyperCamMod: hook ValueAnimator.start terpasang
HyperCamMod: config transisiZoom=1
HyperCamMod: animasi zoom 99ms -> 220ms
```

Baris `hook animasi zoom Zb.g.i gagal` / `FragmentZoomToggle.Yh gagal` **normal**
di kamera 6.7 - itu jalur lama yang memang tidak dipakai lagi.

### Pasang

```powershell
adb install -r HyperCamMod-v1.6.1.apk
```

Lalu force stop aplikasi Kamera (atau tutup dari recents) dan buka lagi.

Kalau terasa masih terlalu cepat, buka **HyperCamMod > Tampilan preview >
Transisi zoom** dan pilih **Santai**. Kalau terlalu lambat, pilih **Halus**
atau **Bawaan**.
