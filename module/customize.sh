#!/system/bin/sh

ui_print " "
ui_print "========================================"
ui_print " HyperCamMod"
ui_print " Mod tampilan kamera HyperOS (systemless)"
ui_print "========================================"
ui_print " "

if [ "$MAGISK_VER_CODE" -lt 26000 ]; then
  ui_print "! Magisk v26+ disarankan untuk modul ini."
fi

if [ -f "$MODPATH/target.txt" ]; then
  TARGET=$(cat "$MODPATH/target.txt")
  ui_print "- Target: $TARGET"
  if [ ! -e "$TARGET" ]; then
    ui_print "! PERINGATAN: APK target tidak ditemukan di ROM ini."
    ui_print "! Modul tetap dipasang, tetapi kemungkinan tidak berefek."
  fi
fi

ui_print "- APK mod akan menimpa kamera asli secara systemless."
ui_print "- Setelah reboot, buka kamera dan uji foto/video/zoom."
ui_print "- Bermasalah? Nonaktifkan modul di Magisk lalu reboot."
ui_print " "
