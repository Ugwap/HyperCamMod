## HyperCamMod v1.4.1

Perbaikan utama: perpindahan preview saat mode **Otomatis** aktif sekarang
**halus**, tidak lagi "patah" / melompat.

### Yang berubah

- **Animasi transisi preview** — saat bar hitam hilang (zoom masuk) atau muncul
  lagi (zoom keluar), skala preview dianimasikan **220 ms** dengan
  `ValueAnimator` + `DecelerateInterpolator`. Sebelumnya perubahan skala
  diterapkan seketika, jadi terlihat melompat.
- **Histeresis ambang zoom** — layar penuh aktif di atas **1,05x**, tapi baru
  kembali 4:3 di bawah **1,02x**. Tujuannya supaya tampilan tidak bolak-balik
  saat jari berhenti tepat di batas ambang.
- **Animasi tidak diulang** oleh layout listener: animasi hanya dijalankan saat
  target benar-benar berubah, jadi tidak ada kedipan saat UI kamera re-layout.
- Nilai zoom yang dipakai diambil dari getter `n0()` (nilai yang tersimpan di
  `ZoomManager`), bukan hanya argumen hook — aman kalau kamera meng-clamp zoom.
- Animasi juga berlaku untuk mode **50%** / **100%** saat pengaturan diubah.

### Tetap seperti v1.4.0

- Mode zoom default **Otomatis**: 0,6x dan 1x tetap 4:3 dengan bar; di-zoom
  preview memenuhi layar.
- Scrim Mati (default) / 25% / 50%.
- Pengaturan dibaca lewat ordered broadcast (`ConfigReceiver`), tidak terpengaruh
  masalah `XSharedPreferences`/SELinux maupun package visibility.

### Catatan

- Perubahan setting tetap berlaku setelah **kamera dibuka ulang** (force stop /
  tutup dari recents).
- Hasil foto/video tetap 4:3 penuh; hanya preview yang berubah.
- Ikon modul tetap bisa disembunyikan; layar modul tetap bisa dibuka dari
  LSPosed Manager.

### Pasang

```powershell
adb install -r HyperCamMod-v1.4.1.apk
```

Lalu di LSPosed Manager: aktifkan modul, scope **Camera** (`com.android.camera`),
force stop kamera, buka lagi.

Cek log:

```powershell
adb logcat -s LSPosedFramework | Select-String "HyperCamMod"
```

Harus muncul `hook ZoomManager.setZoomRatio terpasang` dan `config zoom=3 scrim=0%`.
Saat zoom melewati ambang: `auto zoom aktif (2.0x)` / `auto zoom nonaktif (1.0x)`.
