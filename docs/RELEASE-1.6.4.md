## HyperCamMod v1.6.4

Perbaikan regresi v1.6.3: setelah menekan tombol zoom, sempat terlihat
**zoom-out kecil** sebelum zoom-nya benar-benar jalan.

### Penyebab

v1.6.3 menyaring nilai rasio yang dikirim kamera per frame. Ternyata kamera 6.7
kadang mengirim frame pertama yang sedikit **mundur** dari posisi sekarang
(mis. dari 1,30x ke 1,29x, atau 1,0x ke 0,988x) karena kurva internalnya. Filter
mengikuti nilai itu, jadi gerakan sempat berbalik sebentar sebelum naik/turun ke
target. Di v1.6.2 hal ini tidak terlihat karena animasinya terlalu cepat.

### Yang berubah

| Aspek | v1.6.3 | v1.6.4 |
| --- | --- | --- |
| Target perataan | nilai frame kamera (bisa mundur) | nilai akhir animator (`Vr.j.m/n`, `Zb.g.i`) |
| Titik awal | `sLastRatio` terakhir | rasio aktif dari `ZoomManager.j1()` / `n0()` |
| Arah gerakan | bisa berbalik | selalu satu arah menuju target |
| Pinch saat transisi | ikut disaring | perataan berhenti, ikuti jari |

Cara kerjanya sekarang:

1. Saat animator zoom dibuat, modul mencatat **rasio tujuan** (argumen kedua
   pabrik animator) dan titik awal dari rasio yang benar-benar aktif.
2. Nilai frame kamera tidak lagi dipakai sebagai target - hanya sebagai pemicu
   agar `setZoomRatio` tetap dipanggil di jalur resmi kamera.
3. Posisi diramp dengan filter eksponensial `current += (target - current) x
   (1 - e^(-dt/tau))`, `tau = 0,33 x durasi` (Halus ±106 ms, Santai ±180 ms).
4. Tick 16 ms melanjutkan gerakan setelah animator kamera selesai sampai selisih
   ke target < 0,0015, jadi tidak ada sisa zoom yang menggantung.
5. Kalau pengguna memutar zoom melewati target (pinch), sesi perataan langsung
   dihentikan dan zoom mengikuti jari.

**Bawaan** tetap tidak diubah. Tidak ada perubahan pada capture, video, atau Pro
mode.

### Hasil uji langsung (adb, Poco X7 Pro)

Kamera **6.7.000290.4**, mode **Halus**. Setiap langkah diperiksa: tidak ada satu
pun langkah yang berbalik arah.

| Transisi | Langkah | Arah |
| --- | --- | --- |
| 1x -> 2x (Video) | 40 | naik terus |
| 2x -> 1x | 41 | turun terus |
| 0.6x -> 2x | 70 | naik terus |
| 1x -> 0.6x | 40 | turun terus |
| 0.6x -> 1x | 36 | naik terus |

Tidak ada crash maupun ANR.

Catatan: di 0.6x <-> 1x tetap ada batas perpindahan lensa ultrawide, jadi
transisinya tidak bisa sesempurna 1x <-> 2x.

### Pasang

```powershell
adb install -r HyperCamMod-v1.6.4.apk
```

Lalu force stop aplikasi Kamera (atau tutup dari recents) dan buka lagi.
Disarankan tetap memakai **Halus** (±0,8 detik).
