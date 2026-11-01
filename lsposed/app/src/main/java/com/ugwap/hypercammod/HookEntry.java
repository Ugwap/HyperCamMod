package com.ugwap.hypercammod;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Modul LSPosed: tampilan kamera bawaan HyperOS jadi full-screen ala iOS.
 *
 * Preview 4:3 yang semula menyisakan bar hitam di atas/bawah diperbesar
 * (center-crop) sampai menutup seluruh layar, lalu diberi scrim gradient
 * hitam 50% di area atas/bawah supaya ikon tetap terbaca.
 *
 * Tidak ada capture frame / blur (PixelCopy) supaya tetap ringan.
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String PKG = "com.android.camera";
    private static final String LOG = "HyperCamMod";

    /**
     * Konfigurasi dari layar modul, dibaca dari proses kamera lewat ordered
     * broadcast eksplisit (lihat ConfigReceiver). Bukan XSharedPreferences:
     * di ROM ini LSPosed mengalihkan SharedPreferences modul ke
     * /data/misc/apexdata dan SELinux menolak penulisannya.
     */
    private static final int DEFAULT_ZOOM_MODE = ConfigReceiver.DEFAULT_ZOOM_MODE;
    /** Default tanpa scrim supaya preview tidak terasa berkabut. */
    private static final int DEFAULT_SCRIM = ConfigReceiver.DEFAULT_SCRIM;

    private static long sPrefsCheckedAt;
    private static long sRetryAfter;
    private static boolean sRequestInFlight;
    private static int sLastZoomMode = -1;
    private static int sLastScrim = -1;
    private static boolean sQueryFailed;

    /** Looper thread untuk menerima balasan ordered broadcast. */
    private static Looper sIpcThread;

    /** View terakhir, supaya config yang baru terbaca bisa langsung diterapkan. */
    private static WeakReference<Activity> sLastActivity;
    private static WeakReference<View> sLastDecor;

    /** Mode Otomatis: bar hitam hanya hilang saat preview benar-benar di-zoom. */
    private static final int MODE_AUTO = 3;
    /** Ambang zoom: 0.6x dan 1x masih dianggap "belum zoom". */
    private static final float AUTO_ZOOM_THRESHOLD = 1.05f;

    private static volatile float sZoomRatio = 1f;
    private static WeakReference<Object> sZoomManager;

    private static final String TAG_TOP = "hcm_scrim_top";
    private static final String TAG_BOTTOM = "hcm_scrim_bottom";

    /** Decor view -> sudah dipasangi listener layout. */
    private static final WeakHashMap<View, Boolean> ATTACHED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> WINDOW_DONE = new WeakHashMap<>();

    /** Lapisan hitam bawaan kamera yang menutupi area bar atas/bawah. */
    private static final java.util.HashSet<String> COVER_IDS = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "top_cover_layout", "bottom_cover_layout",
                    "left_cover_layout", "right_cover_layout",
                    "mask_cover_layout", "top_config_background",
                    "cover_top", "cover_bottom", "cover_left", "cover_right"));

    /** Cache id resource per Resources (id bisa berbeda antar versi ROM). */
    private static final WeakHashMap<Resources, int[]> IDS = new WeakHashMap<>();

    private static final int ID_ROOT = 0;
    private static final int ID_LOWER = 1;
    private static final int ID_TOP_BG = 2;
    private static final int ID_PREVIEW = 3;
    private static final int ID_COVER = 4;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) return;

        XposedBridge.log(LOG + ": aktif di " + lpparam.packageName);

        hookShapeBackGroundView(lpparam.classLoader);
        hookZoomManager(lpparam.classLoader);
        hookActivity(lpparam.classLoader);
    }

    // ------------------------------------------------------------------
    // 0. Level zoom kamera (untuk mode Otomatis)
    // ------------------------------------------------------------------

    /**
     * Ambil rasio zoom aktif dari ZoomManager kamera (X5.f = implementasi W5.a).
     * Dipakai mode Otomatis: bar hitam hanya hilang saat rasio > 1 (mis. 2x),
     * sedangkan di 0.6x / 1x preview tetap 4:3 dengan bar.
     */
    private void hookZoomManager(ClassLoader cl) {
        try {
            Class<?> zoomManager = XposedHelpers.findClass("X5.f", cl);

            XposedHelpers.findAndHookMethod(zoomManager, "setZoomRatio", float.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            onZoomRatioChanged(param.thisObject, (Float) param.args[0]);
                        }
                    });

            XposedHelpers.findAndHookMethod(zoomManager, "e0", float.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            onZoomRatioChanged(param.thisObject, (Float) param.args[0]);
                        }
                    });

            XposedBridge.log(LOG + ": hook ZoomManager.setZoomRatio terpasang");
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": hook zoom ratio gagal (mode Otomatis akan pakai 4:3): " + t);
        }
    }

    // ------------------------------------------------------------------
    // 1. Top bar transparan
    // ------------------------------------------------------------------

    private void hookShapeBackGroundView(ClassLoader cl) {
        try {
            Class<?> shape = XposedHelpers.findClass(
                    "com.android.camera.ui.ShapeBackGroundView", cl);

            XposedHelpers.findAndHookMethod(shape, "d", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        View view = (View) param.thisObject;
                        int[] ids = ids(view.getResources());
                        if (ids[ID_TOP_BG] == 0 || view.getId() != ids[ID_TOP_BG]) return;

                        // Paint yang dipakai ShapeBackGroundView untuk menggambar.
                        Paint paint = (Paint) XposedHelpers.getObjectField(view, "e");
                        if (paint != null) paint.setColor(0);
                        paint = (Paint) XposedHelpers.getObjectField(view, "i");
                        if (paint != null) paint.setColor(0);

                        view.setAlpha(0f);
                        view.invalidate();
                    } catch (Throwable t) {
                        XposedBridge.log(LOG + ": gagal memudarkan top bar: " + t);
                    }
                }
            });
            XposedBridge.log(LOG + ": hook ShapeBackGroundView.d() terpasang");
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": hook ShapeBackGroundView gagal: " + t);
        }
    }

    // ------------------------------------------------------------------
    // 2. Layout kamera
    // ------------------------------------------------------------------

    private void hookActivity(ClassLoader cl) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                schedule((Activity) param.thisObject);
            }
        };
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, hook);
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", hook);
            XposedBridge.log(LOG + ": hook Activity.onCreate/onResume terpasang");
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": hook Activity gagal: " + t);
        }
    }

    private static void schedule(final Activity activity) {
        try {
            final Window window = activity.getWindow();
            if (window == null) return;
            final View decor = window.getDecorView();

            decor.post(new Runnable() {
                @Override
                public void run() {
                    apply(activity, decor);
                    attachLayoutListener(activity, decor);
                }
            });
            // Ulangi sekali setelah layout/preview siap (SurfaceView baru muncul belakangan).
            decor.postDelayed(() -> apply(activity, decor), 1200);
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": schedule gagal: " + t);
        }
    }

    /** Dipanggil setiap kali layout selesai (view baru bisa muncul kapan saja). */
    private static void attachLayoutListener(final Activity activity, final View decor) {
        if (ATTACHED.containsKey(decor)) return;
        ATTACHED.put(decor, Boolean.TRUE);
        decor.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        apply(activity, decor);
                    }
                });
    }

    private static void apply(Activity activity, View decor) {
        try {
            sLastActivity = new WeakReference<>(activity);
            sLastDecor = new WeakReference<>(decor);
            reloadPrefs(activity);

            Resources res = activity.getResources();
            int[] ids = ids(res);

            View root = ids[ID_ROOT] == 0 ? null : decor.findViewById(ids[ID_ROOT]);
            if (!(root instanceof FrameLayout)) return;
            FrameLayout container = (FrameLayout) root;

            int scrim = scrimLevel();
            if (scrim <= 0) {
                removeScrims(container);
            } else if (!hasScrims(container, scrim)) {
                boolean landscape = res.getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;

                removeScrims(container);

                View top = makeScrim(activity, true, res, landscape, scrim);
                View bottom = makeScrim(activity, false, res, landscape, scrim);

                int index = childIndexById(container, ids[ID_LOWER]);
                if (index < 0) index = Math.min(1, container.getChildCount());

                container.addView(top, index, params(res, true, landscape));
                container.addView(bottom, index + 1, params(res, false, landscape));

                XposedBridge.log(LOG + ": scrim " + scrim + "% dipasang");
            }

            clearCovers(decor);
            zoomPreview(decor, ids);

            if (ids[ID_TOP_BG] != 0) {
                View topBg = decor.findViewById(ids[ID_TOP_BG]);
                if (topBg != null && topBg.getAlpha() != 0f) {
                    topBg.setAlpha(0f);
                }
            }

            // Status bar ikut tembus pandang supaya preview terlihat di balik scrim.
            if (!WINDOW_DONE.containsKey(decor)) {
                WINDOW_DONE.put(decor, Boolean.TRUE);
                Window window = activity.getWindow();
                if (window != null) {
                    window.setStatusBarColor(Color.TRANSPARENT);
                    window.setNavigationBarColor(Color.TRANSPARENT);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": apply gagal: " + t);
        }
    }

    /**
     * Perbesar preview 4:3 (center-crop) sesuai pengaturan zoom:
     * 0 = tanpa zoom, 1 = setengah dari gap, 2 = penuh layar, 3 = otomatis
     * (penuh layar hanya saat rasio zoom kamera > 1,05x). Skala dihitung dari
     * tinggi area preview asli (bounds gl_root_cover) supaya benar untuk rasio lain.
     */
    private static void zoomPreview(View decor, int[] ids) {
        View previewLayout = ids[ID_PREVIEW] == 0 ? null : decor.findViewById(ids[ID_PREVIEW]);
        if (previewLayout == null) return;

        SurfaceView surface = findSurface(previewLayout);
        if (surface == null) return;

        int w = surface.getWidth();
        int h = surface.getHeight();
        if (w <= 0 || h <= 0) return;

        float contentTop;
        float contentBottom;
        View cover = ids[ID_COVER] == 0 ? null : previewLayout.findViewById(ids[ID_COVER]);
        if (cover != null && cover.getHeight() > 0) {
            contentTop = cover.getTop();
            contentBottom = cover.getBottom();
        } else {
            float contentHeight = w * 4f / 3f;
            contentTop = (h - contentHeight) / 2f;
            contentBottom = contentTop + contentHeight;
        }

        float contentHeight = contentBottom - contentTop;
        if (contentHeight <= 0f || contentHeight > h * 0.98f) {
            // Sudah full-screen / rasio tidak diketahui: jangan diubah.
            resetZoom(surface);
            return;
        }

        int mode = zoomMode();
        float fillScale = h / contentHeight;
        if (fillScale <= 1.001f) {
            resetZoom(surface);
            return;
        }

        float fraction;
        if (mode == MODE_AUTO) {
            // Otomatis: bar hitam hanya hilang saat preview benar-benar di-zoom.
            if (!autoFill()) {
                resetZoom(surface);
                return;
            }
            fraction = 1f;
        } else if (mode <= 0) {
            resetZoom(surface);
            return;
        } else {
            // 50% = separuh gap ditutup, jadi crop dan upscale-nya lebih ringan.
            fraction = mode == 1 ? 0.5f : 1f;
        }

        float scale = 1f + (fillScale - 1f) * fraction;
        float contentCenterY = (contentTop + contentBottom) / 2f;

        surface.setPivotX(w / 2f);
        surface.setPivotY(contentCenterY);
        surface.setScaleX(scale);
        surface.setScaleY(scale);
        surface.setTranslationY(h / 2f - contentCenterY);
    }

    private static void resetZoom(SurfaceView surface) {
        if (surface.getScaleX() != 1f || surface.getTranslationY() != 0f) {
            surface.setPivotX(surface.getWidth() / 2f);
            surface.setPivotY(surface.getHeight() / 2f);
            surface.setScaleX(1f);
            surface.setScaleY(1f);
            surface.setTranslationY(0f);
        }
    }

    private static View makeScrim(Activity activity, boolean top, Resources res,
                                  boolean landscape, int level) {
        // level = persen alpha hitam (0/25/50).
        int alpha = Math.round(255f * level / 100f);
        int scrim = (alpha << 24);
        int[] colors = top
                ? new int[]{scrim, scrim, 0x00000000}
                : new int[]{0x00000000, scrim, scrim};
        float[] stops = top
                ? new float[]{0f, 0.55f, 1f}
                : new float[]{0f, 0.45f, 1f};

        ScrimView view = new ScrimView(activity, colors, stops, level);
        view.setTag(top ? TAG_TOP : TAG_BOTTOM);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setClickable(false);
        view.setFocusable(false);
        return view;
    }

    /** View sederhana yang menggambar gradient dengan posisi warna presisi. */
    private static class ScrimView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors;
        private final float[] stops;
        final int level;

        ScrimView(Context context, int[] colors, float[] stops, int level) {
            super(context);
            this.colors = colors;
            this.stops = stops;
            this.level = level;
            setWillNotDraw(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            paint.setShader(new LinearGradient(
                    0f, 0f, 0f, Math.max(1, h), colors, stops, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        }
    }

    private static FrameLayout.LayoutParams params(Resources res, boolean top, boolean landscape) {
        int heightDp = top ? (landscape ? 120 : 200) : (landscape ? 180 : 320);
        int heightPx = Math.round(heightDp * res.getDisplayMetrics().density);
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx,
                top ? Gravity.TOP : Gravity.BOTTOM);
    }

    /** Sembunyikan lapisan hitam penutup bar atas/bawah (cover) bawaan kamera. */
    private static void clearCovers(View view) {
        if (view.getId() != 0 && view.getAlpha() != 0f) {
            try {
                String name = view.getResources().getResourceEntryName(view.getId());
                if (COVER_IDS.contains(name)) {
                    view.setAlpha(0f);
                }
            } catch (Throwable ignored) {
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearCovers(group.getChildAt(i));
            }
        }
    }

    private static int childIndexById(ViewGroup parent, int id) {
        if (id == 0) return -1;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i).getId() == id) return i;
        }
        return -1;
    }

    /** Cek langsung tipe ScrimView di child teratas (lebih andal daripada tag lookup). */
    private static boolean hasScrims(ViewGroup parent, int level) {
        boolean top = false;
        boolean bottom = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (!(child instanceof ScrimView)) continue;
            if (((ScrimView) child).level != level) return false;
            if (TAG_TOP.equals(child.getTag())) top = true;
            if (TAG_BOTTOM.equals(child.getTag())) bottom = true;
        }
        return top && bottom;
    }

    private static void removeScrims(ViewGroup parent) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            if (parent.getChildAt(i) instanceof ScrimView) {
                parent.removeViewAt(i);
            }
        }
    }

    private static SurfaceView findSurface(View view) {
        if (view instanceof SurfaceView) return (SurfaceView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                SurfaceView surface = findSurface(group.getChildAt(i));
                if (surface != null) return surface;
            }
        }
        return null;
    }

    /**
     * Baca konfigurasi modul lewat ordered broadcast eksplisit. Di-throttle
     * 1 detik supaya tidak IPC terus-menerus saat onGlobalLayout berulang.
     *
     * Broadcast eksplisit dipakai karena paket modul tidak terlihat dari
     * aplikasi Kamera (package visibility): ContentProvider maupun bound
     * Service sama-sama "not found" saat diakses dari proses kamera.
     */
    private static void reloadPrefs(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (now - sPrefsCheckedAt < 1000L || sRequestInFlight || now < sRetryAfter) return;
        sPrefsCheckedAt = now;
        sRequestInFlight = true;

        try {
            Intent intent = new Intent(ConfigReceiver.ACTION);
            intent.setComponent(new ComponentName(ConfigReceiver.PKG, ConfigReceiver.COMPONENT));

            context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent reply) {
                    sRequestInFlight = false;

                    Bundle extras = getResultExtras(false);
                    if (getResultCode() != Activity.RESULT_OK || extras == null) {
                        // Proses modul belum siap / dimatikan: jeda dulu supaya
                        // tidak mengirim broadcast terus-menerus.
                        sRetryAfter = SystemClock.elapsedRealtime() + 10000L;
                        if (!sQueryFailed) {
                            sQueryFailed = true;
                            XposedBridge.log(LOG + ": pengaturan modul belum bisa dibaca");
                        }
                        return;
                    }

                    int zoom = extras.getInt(ConfigReceiver.KEY_ZOOM_MODE, DEFAULT_ZOOM_MODE);
                    int scrim = extras.getInt(ConfigReceiver.KEY_SCRIM, DEFAULT_SCRIM);
                    zoom = zoom < 0 ? 0 : Math.min(zoom, MODE_AUTO);
                    scrim = scrim <= 0 ? 0 : Math.min(scrim, 50);

                    if (sQueryFailed) {
                        sQueryFailed = false;
                        XposedBridge.log(LOG + ": pengaturan modul terbaca lagi");
                    }

                    if (zoom != sLastZoomMode || scrim != sLastScrim) {
                        sLastZoomMode = zoom;
                        sLastScrim = scrim;
                        XposedBridge.log(LOG + ": config zoom=" + zoom + " scrim=" + scrim + "%");
                        reapplyLast();
                    }
                }
            }, new Handler(ipcLooper()), Activity.RESULT_OK, null, null);
        } catch (Throwable t) {
            sRequestInFlight = false;
            sRetryAfter = SystemClock.elapsedRealtime() + 10000L;
            if (!sQueryFailed) {
                sQueryFailed = true;
                XposedBridge.log(LOG + ": baca config gagal: " + t);
            }
        }
    }

    /** Setelah config berubah, terapkan ulang ke view yang sedang tampil. */
    private static void reapplyLast() {
        Activity activity = sLastActivity == null ? null : sLastActivity.get();
        View decor = sLastDecor == null ? null : sLastDecor.get();
        if (activity == null || decor == null) return;
        activity.runOnUiThread(() -> apply(activity, decor));
    }

    /** Looper thread terpisah supaya balasan broadcast bisa masuk saat UI sibuk. */
    private static Looper ipcLooper() {
        if (sIpcThread == null) {
            HandlerThread thread = new HandlerThread("hcm-ipc");
            thread.start();
            sIpcThread = thread.getLooper();
        }
        return sIpcThread;
    }

    private static int zoomMode() {
        return sLastZoomMode < 0 ? DEFAULT_ZOOM_MODE : sLastZoomMode;
    }

    private static int scrimLevel() {
        return sLastScrim < 0 ? DEFAULT_SCRIM : sLastScrim;
    }

    /** Mode Otomatis: preview dianggap "sudah di-zoom" kalau rasio > 1.05x. */
    private static boolean autoFill() {
        Object manager = sZoomManager == null ? null : sZoomManager.get();
        if (manager != null) {
            try {
                float ratio = (Float) XposedHelpers.callMethod(manager, "n0");
                if (ratio > 0f && !Float.isNaN(ratio)) sZoomRatio = ratio;
            } catch (Throwable ignored) {
                // Pakai nilai cache kalau getter tidak tersedia.
            }
        }
        return sZoomRatio > AUTO_ZOOM_THRESHOLD;
    }

    /** Dipanggil dari hook ZoomManager saat rasio zoom berubah. */
    private static void onZoomRatioChanged(Object manager, Float ratio) {
        if (manager != null) sZoomManager = new WeakReference<>(manager);

        float value = ratio == null || Float.isNaN(ratio) ? 0f : ratio;
        // Utamakan nilai yang benar-benar tersimpan di ZoomManager (bisa saja
        // argumen hook masih nilai mentah sebelum di-clamp).
        if (manager != null) {
            try {
                float committed = (Float) XposedHelpers.callMethod(manager, "n0");
                if (committed > 0f && !Float.isNaN(committed)) value = committed;
            } catch (Throwable ignored) {
                // Getter tidak tersedia: pakai nilai dari argumen.
            }
        }
        if (value <= 0f) return;

        boolean wasFill = autoFill();
        sZoomRatio = value;
        boolean nowFill = sZoomRatio > AUTO_ZOOM_THRESHOLD;
        if (wasFill == nowFill || zoomMode() != MODE_AUTO) return;

        XposedBridge.log(LOG + ": auto zoom " + (nowFill ? "aktif" : "nonaktif")
                + " (" + value + "x)");
        reapplyLast();
    }

    private static int[] ids(Resources res) {
        int[] cached = IDS.get(res);
        if (cached != null) return cached;
        int[] ids = new int[5];
        ids[ID_ROOT] = res.getIdentifier("camera_app_root", "id", PKG);
        ids[ID_LOWER] = res.getIdentifier("module_content_lower_layer", "id", PKG);
        ids[ID_TOP_BG] = res.getIdentifier("top_config_background", "id", PKG);
        ids[ID_PREVIEW] = res.getIdentifier("preview_surface_layout", "id", PKG);
        ids[ID_COVER] = res.getIdentifier("gl_root_cover", "id", PKG);
        IDS.put(res, ids);
        return ids;
    }
}
