## HyperCamMod v1.5.1

Opsi **latar hitam saat zoom dihapus** — hasilnya tidak pas di layar, jadi
fitur itu dibuang seluruhnya. Yang tersisa hanya fitur yang sudah stabil.

### Yang baru (sejak v1.4.1)

- **Sudut preview membulat** — pilihan **Mati** / **20 dp** / **40 dp**.
  Memakai `SurfaceView.setCornerRadius` milik sistem: sudut surface-nya ikut
  dibulatkan, jadi tetap rapi saat preview di-zoom/scale. Kalau API itu tidak
  ada di ROM lain, sudut tetap kotak dan alasannya dicatat di log
  (`sudut preview gagal`) — tidak ada efek samping.

### Yang dihapus

- **Latar hitam saat zoom (30% / 40%)** — dihapus dari aplikasi dan dari hook.
  Tidak ada lagi lapisan tambahan saat preview di-zoom; tampilan kembali bersih
  mengikuti mode zoom dan scrim yang dipilih.

### Tetap seperti sebelumnya

- Mode zoom default **Otomatis** (0,6x/1x tetap 4:3, di-zoom jadi penuh layar),
  transisi dianimasikan 220 ms dengan histeresis 1,05x / 1,02x.
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
adb install -r HyperCamMod-v1.5.1.apk
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
HyperCamMod: config zoom=3 scrim=0% sudut=0dp
HyperCamMod: auto zoom aktif (2.0x)
```
