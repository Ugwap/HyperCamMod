# Rencana & analisis kelayakan

Tujuan: mengubah **tampilan** aplikasi kamera HyperOS tanpa merusak fungsi kamera.

## 1. Yang perlu dicek dari APK asli

Jalankan `scripts/inspect.ps1` setelah APK di-pull. Hasil yang menentukan jalur:

### a. `android:sharedUserId`

```xml
<manifest ... android:sharedUserId="android.uid.system">
```

- **Ada** → APK hasil re-sign tidak bisa dipakai sebagai system uid (signature beda).
  Jalur APK-replace kemungkinan besar gagal; pakai LSPosed atau patch smali (lebih rapuh).
- **Tidak ada** → re-sign + Magisk replace masih mungkin.

### b. Pengecekan signature di kode

Cari di smali: `getPackageInfo`, `GET_SIGNATURES`, `GET_SIGNING_CERTIFICATES`,
`checkSignatures`, nama package sendiri, atau kelas anti-tamper.
Kalau ada, harus di-patch (tidak selalu mudah) atau pakai LSPosed.

### c. UI-nya XML atau Compose?

- `decoded/res/layout/*.xml` banyak dan dipakai → edit layout/warna langsung di apktool.
- dominan `androidx.compose` / `miuix` → layout bukan XML; perubahan tampilan lewat
  resource (warna/dimens/string) saja, atau hook Compose di LSPosed.

### d. Dependensi framework MIUI

Camera HyperOS memakai `miui.*` / `miuix.*` dari ROM. Karena APK tetap dijalankan di
HP yang sama, dependensi ini biasanya tetap tersedia. Yang jadi masalah hanya signature
dan shared uid.

### e. Split APK / native libs

Kalau ada `lib/arm64-v8a/*.so`, jangan hapus. Cek juga `pm path` mengembalikan lebih dari
satu APK (split) — base APK saja biasanya cukup untuk app sistem.

## 2. Jalur yang dipilih berdasarkan hasil

| Kondisi | Jalur |
| --- | --- |
| Tanpa sharedUserId, tanpa cek signature, UI XML | **A. Re-sign + Magisk replace** (paling bersih) |
| Ada sharedUserId / cek signature | **B. LSPosed hook** (tanpa ubah APK) |
| UI Compose, hanya mau ganti warna/ikon/teks | **C. Overlay resource** (bisa gagal di Android 12+ kalau resource tidak `overlayable`) |

Jalur A sudah disiapkan penuh di repo ini. Jalur B/C menyusul setelah APK dianalisis.

## 3. Perubahan tampilan yang realistis di jalur A

- Warna aksen, latar panel, warna teks (`res/values/colors.xml`, `styles.xml`)
- Bentuk/ukuran tombol, jarak, posisi elemen (`res/layout/*.xml`, `res/values/dimens.xml`)
- Ikon (`res/drawable*/`, `mipmap*`)
- Teks/label (`res/values*/strings.xml`)
- Menyembunyikan elemen tertentu (set `visibility="gone"`)

Yang **tidak** realistis tanpa menulis ulang kode: mengubah alur kamera, menambah fitur
baru, atau mengganti renderer preview.

## 4. Batasan penting

- Setiap update ROM/HyperOS akan menimpa APK asli; modul harus di-build ulang dari APK
  versi baru.
- APK yang di-sign ulang tidak lagi punya signature Xiaomi. Izin signature-level
  (mis. akses hardware tertentu) bisa hilang.
- Play Integrity / status "device certified" bisa terpengaruh.
- Kamera adalah aplikasi kritis: selalu simpan APK asli dan siap reboot ke recovery
  untuk menghapus modul kalau bootloop.

## 5. Checklist kerja

- [ ] Pull APK + framework (`pull-stock.ps1`)
- [ ] Inspect manifest & smali (`inspect.ps1`)
- [ ] Tentukan jalur A/B/C
- [ ] Decode + edit tampilan
- [ ] Build + sign + verify
- [ ] Buat modul + flash + reboot
- [ ] Uji: buka kamera, foto, video, zoom, pro mode
- [ ] Dokumentasikan hasil & cara revert
