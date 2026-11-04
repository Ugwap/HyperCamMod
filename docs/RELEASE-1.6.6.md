## HyperCamMod v1.6.6

Membuat transisi zoom **lebih pelan dan halus**. Perbaikan anti zoom-out dari
v1.6.5 tetap dipakai - tidak ada perubahan pada logika arah gerakan.

### Yang berubah

| Setelan | Sebelum (v1.6.5) | Sesudah (v1.6.6) |
| --- | --- | --- |
| **Halus** (default) | `durasi x 3,2`, 320-620 ms | `durasi x 4,5`, **440-880 ms** |
| **Santai** | `durasi x 5,0`, 550-1000 ms | `durasi x 7,0`, **750-1400 ms** |
| **Bawaan** | tidak diubah | tidak diubah |

Dengan durasi yang lebih panjang, jarak zoom per frame jadi lebih kecil (sekitar
10% lebih kecil di awal gerakan), jadi perpindahan 1x <-> 2x dan 0.6x <-> 1x
terasa lebih mengalir.

Kurva tetap sama: cepat di awal, melambat mendekati target, dan **tepat mendarat
di target** saat animator kamera selesai (perbaikan v1.6.5). Jadi tidak ada lagi
gerakan balik setelah sampai target.

### Kalau masih terasa terlalu cepat

Pilih **Santai** di aplikasi modul (750-1400 ms). Kalau malah terasa terlalu
lambat, pilih **Bawaan** untuk membandingkan dengan kecepatan asli kamera.

### Hasil uji

- Kamera 6.7.000290.4, Poco X7 Pro, mode Halus
- Durasi animator terpasang 440 ms (sebelumnya 320 ms)
- Transisi 1x -> 2x, 2x -> 1x, 1x -> 0.6x, 0.6x -> 1x: satu arah, tanpa crash/ANR
- Hook 6.4 (`Zb.g.i`, `FragmentZoomToggle`) tetap gagal dengan catatan di log -
  normal, karena ROM ini memakai kamera 6.7

### Pasang

```powershell
adb install -r HyperCamMod-v1.6.6.apk
```

Lalu force stop aplikasi Kamera (atau tutup dari recents) dan buka lagi.
