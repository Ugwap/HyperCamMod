## HyperCamMod v1.6.3

Zoom yang benar-benar mulus di kamera **6.7**. Di v1.6.2 animator zoom sudah
diperpanjang dan kurvanya diganti, tapi di 6.7 hasilnya masih terasa patah:
kamera 6.7 bukan cuma menggerakkan animator, ia juga menghitung ulang rasio zoom
lewat spline-nya sendiri setiap frame, sehingga kurva akhirnya tetap punya
perubahan kecepatan yang tajam.

v1.6.3 memakai pendekatan yang berbeda: nilai rasio zoom **disaring per frame**
dengan *exponential smoothing*, bukan hanya memperpanjang animator.

### Yang berubah

| Aspek | v1.6.2 | v1.6.3 |
| --- | --- | --- |
| Dasar | animator diperpanjang + ganti kurva | animator diperpanjang + kurva + **smoothing nilai rasio** |
| Filter | tidak ada | `current += (target - current) x (1 - e^(-dt/tau))` |
| `tau` | - | `0,33 x durasi` (Halus ±106 ms, Santai ±180 ms) |
| Ekor gerakan | berhenti saat animator selesai | tick 16 ms sampai selisih < 0,0015 |
| Cakupan | foto | foto + video |

Detail perilaku:

- Filter hanya aktif ±2,5 detik setelah animator zoom kamera dibuat, jadi
  **pinch-to-zoom manual tetap mengikuti jari** dan tidak ikut diperlambat.
- Tick lanjutan berjalan di main looper dengan guard anti-rekursi
  (`sSmoothApplying`), dan nilai akhir dipaksa tepat sama dengan target.
- **Bawaan** tidak diubah sama sekali. **Halus** dan **Santai** tetap dua level
  seperti sebelumnya.

Hook baru: `setZoomRatio(float)` pada `k9.j` (kamera 6.7) / `X5.f` (kamera 6.4).
Selain itu tidak ada perubahan pada capture, video, atau Pro mode.

### Hasil uji langsung (adb, Poco X7 Pro)

Kamera **6.7.000290.4**. Langkah dihitung dari log rasio per frame (tick ±16 ms):

| Transisi | Setelan | Langkah | Durasi |
| --- | --- | --- | --- |
| 1x -> 2x | Halus | 53 | 851 ms |
| 2x -> 1x | Halus | 56 | 813 ms |
| 1x -> 0.6x | Halus | 58 | 834 ms |
| 0.6x -> 1x | Halus | 44 | 728 ms |
| 1x -> 2x | Santai | 86 | 1466 ms |
| 2x -> 1x | Santai | 89 | 1386 ms |
| 1x -> 2x (Video) | Santai | 74 | 1179 ms |

Sebelumnya di 6.7 animator hanya 67-171 ms dengan gerakan yang tidak rata.
Tidak ada crash maupun ANR di semua jalur yang diuji.

Catatan: di 0.6x <-> 1x tetap ada batas perpindahan lensa ultrawide, jadi
transisinya tidak bisa sesempurna 1x <-> 2x. Modul tidak memaksa lensa ultrawide
sampai 1x (kualitas 1x akan turun), jadi batas itu dibiarkan.

### Pasang

```powershell
adb install -r HyperCamMod-v1.6.3.apk
```

Lalu force stop aplikasi Kamera (atau tutup dari recents) dan buka lagi.

Disarankan pakai **Halus** (±0,8 detik). Kalau masih terasa cepat, pilih
**Santai** (±1,4 detik) di **HyperCamMod > Tampilan preview > Transisi zoom**.
Kalau justru terlalu lambat, pilih **Bawaan**.
