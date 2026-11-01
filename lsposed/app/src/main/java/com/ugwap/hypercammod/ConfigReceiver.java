package com.ugwap.hypercammod;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

/**
 * Menjawab permintaan konfigurasi dari proses kamera yang di-hook.
 *
 * Kenapa bukan XSharedPreferences: di ROM ini LSPosed mengalihkan
 * SharedPreferences modul ke /data/misc/apexdata dan SELinux menolak
 * penulisannya, jadi pengaturan tidak pernah tersimpan.
 *
 * Kenapa bukan ContentProvider / bound Service: paket modul tidak terlihat
 * oleh aplikasi Kamera (package visibility), sehingga keduanya "not found".
 * Broadcast eksplisit tidak kena filter itu. Balasan dikirim lewat
 * setResultExtras pada ordered broadcast.
 */
public class ConfigReceiver extends BroadcastReceiver {

    public static final String PKG = "com.ugwap.hypercammod";
    public static final String ACTION = "com.ugwap.hypercammod.action.GET_CONFIG";
    public static final String COMPONENT = PKG + ".ConfigReceiver";

    public static final String PREFS_NAME = "hypercammod";
    public static final String KEY_ZOOM_MODE = "zoom_mode";   // 0=mati, 1=50%, 2=penuh, 3=otomatis
    public static final String KEY_SCRIM = "scrim_level";     // 0, 25, 50 (persen)
    public static final String KEY_ZOOM_BG = "zoom_bg";       // 0, 30, 40 (persen, hanya saat zoom)
    public static final String KEY_CORNER = "corner_radius";  // 0, 20, 40 (dp)
    public static final int DEFAULT_ZOOM_MODE = 3;  // 3 = Otomatis
    public static final int DEFAULT_SCRIM = 0;
    public static final int DEFAULT_ZOOM_BG = 0;
    public static final int DEFAULT_CORNER = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle result = new Bundle();
        result.putInt(KEY_ZOOM_MODE, readZoomMode(context));
        result.putInt(KEY_SCRIM, readScrim(context));
        result.putInt(KEY_ZOOM_BG, readZoomBg(context));
        result.putInt(KEY_CORNER, readCorner(context));

        // Hanya berpengaruh kalau dipanggil lewat ordered broadcast.
        setResultCode(Activity.RESULT_OK);
        setResultExtras(result);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int readZoomMode(Context context) {
        return prefs(context).getInt(KEY_ZOOM_MODE, DEFAULT_ZOOM_MODE);
    }

    public static int readScrim(Context context) {
        return prefs(context).getInt(KEY_SCRIM, DEFAULT_SCRIM);
    }

    public static int readZoomBg(Context context) {
        return prefs(context).getInt(KEY_ZOOM_BG, DEFAULT_ZOOM_BG);
    }

    public static int readCorner(Context context) {
        return prefs(context).getInt(KEY_CORNER, DEFAULT_CORNER);
    }
}
