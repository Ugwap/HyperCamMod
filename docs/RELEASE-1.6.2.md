## HyperCamMod v1.6.2

Zoom yang lebih halus. Di v1.6.1 animasi zoom memang sudah diperpanjang, tapi
masih terasa "patah dan cepat": durasinya hanya ~220 ms dan kurvanya
`DecelerateInterpolator` (paling cepat di awal, langsung melambat). Di kamera
6.7, animator zoom bawaan cuma 73-171 ms, jadi hasilnya masih terasa seperti
lompatan.

### Yang berubah

| Mode | v1.6.1 | v1.6.2 |
| --- | --- | --- |
| Halus (default) | `x 2,2`, 220-460 ms, decelerate | `x 3,2`, 320-620 ms, `PathInterpolator(0.4, 0, 0.2, 1)` |
| Santai | `x 3,4`, 320-700 ms, decelerate | `x 5,0`, 550-1000 ms, kurva sama |
| Bawaan | tidak diubah | tidak diubah |

Kurva baru (cubic-bezier `0.4, 0, 0.2, 1`) membuat zoom mulai pelan, mengebut
di tengah, lalu melambat halus di akhir - bukan sentakan cepat di awal seperti
decelerate.

Tidak ada perubahan pada capture, video, atau Pro mode; hanya animasi zoom di
preview. Tidak ada hook baru, jadi kompatibilitas kamera 6.7 dan 6.4 tetap
sama seperti v1.6.1.

### Hasil uji langsung (adb, Poco X7 Pro)

Kamera **6.7.000290.4**, mode **Halus**:

```
animasi zoom 99ms  -> 320ms
animasi zoom 171ms -> 547ms
animasi zoom 73ms  -> 320ms
```

Sebelumnya (v1.6.1): 99 ms -> 220 ms, 171 ms -> 376 ms, 73 ms -> 220 ms.

### Pasang

```powershell
adb install -r HyperCamMod-v1.6.2.apk
```

Lalu force stop aplikasi Kamera (atau tutup dari recents) dan buka lagi.

Kalau **Halus** masih terasa cepat, pilih **Santai** di
**HyperCamMod > Tampilan preview > Transisi zoom**. Kalau justru terlalu
lambat, pilih **Bawaan**.
