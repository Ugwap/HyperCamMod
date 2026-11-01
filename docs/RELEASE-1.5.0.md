## HyperCamMod v1.5.0

Dua opsi tampilan baru: **latar hitam yang hanya muncul saat zoom** dan
**sudut preview membulat**.

### Yang baru

- **Latar hitam saat zoom** (Mati / **30%** / **40%**)
  - Berupa gradient lembut di area atas/bawah (gelap menempel di tepi layar lalu
    cepat memudar), dan hanya muncul ketika preview sedang penuh — mode
    **Otomatis** yang sudah di-zoom, atau mode **100%**.
  - Dibuat lebih lembut dari scrim biasa supaya tidak terlihat seperti bar yang
    dipotong di tengah gambar.
  - Di 0,6x / 1x (bar hitam masih ada) latar ini tidak terlihat, jadi preview
    tetap terang.
  - Kalau scrim biasa juga aktif, dipakai level yang lebih tinggi — tidak
    ditumpuk supaya tidak terlalu gelap.
- **Sudut preview** (Mati / **20 dp** / **40 dp**)
  - Membulatkan sudut preview memakai `SurfaceView.setCornerRadius` milik
    sistem: sudut surface-nya ikut dibulatkan, jadi tetap rapi saat preview
    di-zoom/scale.
  - Kalau API itu tidak ada di ROM lain, sudut tetap kotak dan alasannya
    dicatat di log (`sudut preview gagal`) — tidak ada efek samping lain.

### Tetap seperti sebelumnya

- Mode zoom default **Otomatis**, dengan animasi transisi **220 ms** dan
  histeresis 1,05x / 1,02x (tidak berkedip di batas ambang).
- Scrim gradient Mati / 25% / 50%.
- Lapisan hitam bawaan kamera dimatikan, top bar & status bar transparan.
- Pengaturan dibaca lewat ordered broadcast (`ConfigReceiver`).

### Catatan

- Perubahan setting berlaku setelah **kamera dibuka ulang** (force stop /
  tutup dari recents).
- Hasil foto/video tidak berubah; hanya tampilan preview.
- Ikon modul tetap bisa disembunyikan; layar modul tetap bisa dibuka dari
  LSPosed Manager.

### Pasang

```powershell
adb install -r HyperCamMod-v1.5.0.apk
```

Lalu di LSPosed Manager: aktifkan modul, scope **Camera** (`com.android.camera`),
force stop kamera, buka lagi.

Cek log:

```powershell
adb logcat -s LSPosedFramework | Select-String "HyperCamMod"
```

Contoh baris yang diharapkan:

```
HyperCamMod: hook ZoomManager.setZoomRatio terpasang
HyperCamMod: config zoom=3 scrim=0% zoombg=30% sudut=20dp
HyperCamMod: auto zoom aktif (2.0x)
HyperCamMod: sudut preview = 55px
```
