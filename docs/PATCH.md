# Patch: scrim gradient atas/bawah ala iOS

## Apa yang diubah

1. **Dua gradient scrim** ditambahkan ke layout utama kamera (`esc.xml` dan `ese.xml`):
   - atas: `#80000000` → transparan, tinggi 200dp
   - bawah: `#80000000` → transparan, tinggi 320dp
2. **Top bar dibuat transparan** supaya preview kamera terlihat di belakang ikon
   dan menyatu dengan gradient (sebelumnya bar atas solid hitam).
   Dilakukan dengan patch kecil di `ShapeBackGroundView` (hanya untuk view dengan
   id `top_config_background`).

Tidak ada logika kamera yang diubah: tidak menyentuh capture, video, zoom, atau
Pro mode.

## Kenapa gradient, bukan blur asli?

Blur live di belakang UI butuh membaca frame preview dan memprosesnya tiap frame
(berat, dan tidak bisa dilakukan lewat XML/resource saja). Aplikasi kamera iOS
sendiri memakai **gradient hitam**, bukan frosted blur, di area kontrolnya. Jadi
gradient ini yang meniru tampilan iOS sekaligus tetap ringan.

## Cara pakai

```powershell
powershell -ExecutionPolicy Bypass -File scripts\pull-stock.ps1
powershell -ExecutionPolicy Bypass -File scripts\decode.ps1
powershell -ExecutionPolicy Bypass -File scripts\patch-ios-scrim.ps1
powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1
powershell -ExecutionPolicy Bypass -File scripts\make-module.ps1
```

Lalu flash `build\HyperCamMod-v0.1.0.zip` di Magisk dan reboot.

## Detail patch smali

File: `smali_classes*/com/android/camera/ui/ShapeBackGroundView.smali`
Metode: `d()V`

Setelah warna background diambil dari resource, ditambahkan pengecekan id:

```smali
invoke-virtual {p0}, Landroid/view/View;->getId()I
move-result v3
const v2, 0x7f0b08e2          # id/top_config_background (dibaca dari public.xml)
if-ne v3, v2, :cond_hcm_transparent
const/4 v1, 0x0               # transparan
:cond_hcm_transparent
```

ID dibaca otomatis oleh `patch-ios-scrim.ps1` dari `public.xml`, jadi aman untuk
versi ROM yang id-nya berbeda. Kalau pola smali berubah, skrip akan berhenti
dengan pesan error (tidak merusak file).

## Batasan

- Ini **bukan** redesign penuh. Layout, ikon, dan mode carousel tetap HyperOS.
  Nama layout di APK diobfuscate (mis. `e_g.xml`), jadi mengubah struktur besar
  tidak realistis lewat resource saja.
- APK harus di-sign ulang. Kalau ROM memakai pengecekan signature/`sharedUserId`,
  kamera bisa gagal jalan — lihat `docs/RENCANA.md` untuk jalur LSPosed.
- Setiap update HyperOS, patch harus dijalankan ulang dari APK versi baru.
