## HyperCamMod v1.3.0

Preview kamera HyperOS sekarang **bisa diatur** — tidak lagi dipaksa full-screen
dengan scrim 50%.

### Yang baru

- **Zoom preview** (default 50%)
  - **Mati** — 4:3 asli, bar hitam atas/bawah kembali, paling tajam
  - **50%** — bar tertutup separuh, crop ringan
  - **100%** — penuh layar, crop paling banyak
- **Scrim gradient atas/bawah** (default **Mati**)
  - **Mati** — area berkabut hilang total
  - **25%** dan **50%** — kalau ikon terasa kurang kontras
- Semua opsi ada di layar modul: **Tampilan preview**.

### Perbaikan

- Pengaturan benar-benar tersimpan. Sebelumnya `XSharedPreferences` gagal karena
  LSPosed mengalihkan SharedPreferences modul ke `/data/misc/apexdata` dan SELinux
  menolak penulisannya. Sekarang konfigurasi dibaca proses kamera lewat ordered
  broadcast eksplisit (`ConfigReceiver`) — tidak butuh permission tambahan.
- Tidak ada lagi efek "berkabut" bawaan: scrim hanya dipasang kalau diset > 0%.

### Catatan

- Perubahan setting berlaku setelah **kamera dibuka ulang** (force stop / tutup dari
  recents).
- Hasil foto/video tetap 4:3 penuh; hanya preview yang di-zoom.
- Ikon modul tetap bisa disembunyikan, dan layar modul tetap bisa dibuka dari
  LSPosed Manager.

### Pasang

```powershell
adb install -r HyperCamMod-v1.3.0.apk
```

Lalu di LSPosed Manager: aktifkan modul, scope **Camera** (`com.android.camera`),
force stop kamera, buka lagi.
