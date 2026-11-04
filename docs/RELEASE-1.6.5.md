## HyperCamMod v1.6.5

Memperbaiki sisa **zoom-out kecil setelah zoom sampai di target** (terlihat di
1x <-> 2x, 0.6x <-> 1x, dan zoom video). Ini lanjutan dari perbaikan v1.6.4.

### Penyebab

Ramp perataan v1.6.4 memakai filter eksponensial yang **tidak pernah benar-benar
mendarat** di target. Pada durasi animator 320 ms, posisinya baru ~95% (mis.
1,9548 dari target 2,0). Sementara itu kamera menyelesaikan animatornya sendiri
dan meng-commit target penuh (2,0) ke jalur zoom resminya.

Urutannya jadi:

1. animator kamera selesai -> kamera menaruh zoom di **2,0**
2. tick perataan modul masih di **1,955**
3. tick lanjutan menarik zoom dari 2,0 ke 1,955, lalu naik lagi ke 2,0

Itulah gerakan balik kecil yang terasa setelah sampai target. Nilai rasio yang
dilog tetap monoton, karena gerakan baliknya terjadi di jalur commit kamera -
bukan di nilai yang dikirim modul.

### Yang berubah

| Aspek | v1.6.4 | v1.6.5 |
| --- | --- | --- |
| Kurva ramp | eksponensial, sisa 2-5% di akhir | eksponensial dinormalisasi, tepat 100% di akhir |
| Waktu mulai | saat animator dibuat | saat frame zoom pertama datang |
| Akhir animator | tick lanjutan menarik sisa | posisi dipaksa tepat di target, frame susulan tidak boleh mundur |
| Setelah target | filter lepas begitu dekat | target ditahan sampai animator kamera benar-benar berhenti |

Rumusnya sekarang:

```text
posisi = awal + (target - awal) x (1 - e^(-3t)) / (1 - e^(-3))
t      = waktu berjalan / durasi animator
```

Bentuk kurvanya sama seperti sebelumnya (cepat di awal, melambat di akhir), jadi
rasanya tetap "Halus" - bedanya sekarang tidak ada sisa jarak di ujung.

**Bawaan** tetap tidak diubah. Tidak ada perubahan pada capture, video, atau Pro
mode. Pinch-to-zoom tetap mengikuti jari seperti biasa.

### Hasil uji langsung (adb, Poco X7 Pro)

Kamera **6.7.000290.4**, mode **Halus**. Semua nilai zoom (tick + frame kamera)
digabung urut waktu, lalu diperiksa satu per satu:

| Transisi | Sampel | Langkah berbalik |
| --- | --- | --- |
| 1x -> 2x | 22 | 0 |
| 2x -> 1x | 23 | 0 |
| 1x -> 0.6x | 23 | 0 |
| 0.6x -> 1x | 24 | 0 |
| 1x -> 2x (Video) | 23 | 0 |

Pemeriksaan visual lewat rekaman layar (screenrecord + pelacakan skala frame)
juga menunjukkan skala berhenti dan **datar** setelah target, tanpa penurunan
balik. Tidak ada crash maupun ANR.

### Pasang

```powershell
adb install -r HyperCamMod-v1.6.5.apk
```

Lalu force stop aplikasi Kamera (atau tutup dari recents) dan buka lagi.
Disarankan tetap memakai **Halus** (±0,8 detik).
