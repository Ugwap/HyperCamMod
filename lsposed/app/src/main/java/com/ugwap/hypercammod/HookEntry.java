package com.ugwap.hypercammod;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
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
    /** Default sudut kotak (tanpa pembulatan). */
    private static final int DEFAULT_CORNER = ConfigReceiver.DEFAULT_CORNER;
    /** Default transisi zoom halus. */
    private static final int DEFAULT_ZOOM_ANIM = ConfigReceiver.DEFAULT_ZOOM_ANIM;

    private static long sPrefsCheckedAt;
    private static long sRetryAfter;
    private static boolean sRequestInFlight;
    private static int sLastZoomMode = -1;
    private static int sLastScrim = -1;
    private static int sLastCorner = -1;
    private static int sLastZoomAnim = -1;
    private static long sLastZoomAnimLogAt;
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
    /** Ambang turun (histeresis) supaya tampilan tidak bolak-balik di batas zoom. */
    private static final float AUTO_ZOOM_RELEASE = 1.02f;

    private static volatile float sZoomRatio = 1f;
    private static volatile boolean sAutoFilled;
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
        hookZoomAnimation(lpparam.classLoader);
        hookActivity(lpparam.classLoader);
    }

    // ------------------------------------------------------------------
    // 0b. Transisi zoom (durasi animasi zoom kamera)
    // ------------------------------------------------------------------

    /**
     * Kamera HyperOS menganimasikan perpindahan zoom (tombol 0,6x/1x/2x atau
     * snap slider) dengan ValueAnimator ber-interpolator Linear dan durasi
     * hampir sama dengan selisih zoom-nya - mis. 1x ke 2x hanya ~100 ms dan
     * 0 ms kalau dianggap instan. Hasilnya terasa melompat.
     *
     * Hook di dua tempat pembuatan animator itu, lalu durasi dan kurvanya
     * diganti supaya perpindahan zoom melambat di akhir:
     * - Vr.j.m / Vr.j.n (kamera 6.7+): durasi baru dipasang pemanggil
     *   setelahnya (0/100/150 ms), jadi animator ditandai dulu lalu dirapikan
     *   tepat sebelum start().
     * - Zb.g.i(float, float): dipakai FragmentZoomToggle / ZoomPanel / DualMenu
     *   di kamera 6.4.
     * - FragmentZoomToggle.Yh(float, float): jalur animasi cadangan kamera 6.4.
     */
    private void hookZoomAnimation(ClassLoader cl) {
        hookZoomAnimFactory(cl, "Vr.j", "m");
        hookZoomAnimFactory(cl, "Vr.j", "n");
        hookAnimatorStart();

        try {
            Class<?> helper = XposedHelpers.findClass("Zb.g", cl);
            XposedHelpers.findAndHookMethod(helper, "i", float.class, float.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object result = param.getResult();
                            if (result instanceof ValueAnimator) {
                                rememberTarget((Float) param.args[1]);
                                smoothZoomAnim((ValueAnimator) result);
                            }
                        }
                    });
            XposedBridge.log(LOG + ": hook animasi zoom (Zb.g.i) terpasang");
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": hook animasi zoom Zb.g.i gagal: " + t);
        }

        try {
            Class<?> toggle = XposedHelpers.findClass(
                    "com.android.camera.fragment.dual.FragmentZoomToggle", cl);
            XposedHelpers.findAndHookMethod(toggle, "Yh", float.class, float.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object anim = XposedHelpers.getObjectField(param.thisObject, "f");
                                if (anim instanceof ValueAnimator) {
                                    rememberTarget((Float) param.args[1]);
                                    smoothZoomAnim((ValueAnimator) anim);
                                }
                            } catch (Throwable ignored) {
                                // Field animator tidak ada di versi ROM ini.
                            }
                        }
                    });
            XposedBridge.log(LOG + ": hook animasi zoom (FragmentZoomToggle.Yh) terpasang");
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": hook animasi zoom Yh gagal: " + t);
        }
    }

    /** Animator zoom yang sudah dibuat tapi belum di-start. */
    private static final java.util.Set<ValueAnimator> ZOOM_ANIMS =
            java.util.Collections.newSetFromMap(
                    new WeakHashMap<ValueAnimator, Boolean>());

    /** Nilai zoom tujuan (argumen kedua) dari animator zoom yang baru dibuat. */
    private static volatile float sPendingTarget;
    private static volatile long sPendingTargetAt;

    private static void rememberTarget(float to) {
        if (!(to > 0f) || Float.isNaN(to)) return;
        sPendingTarget = to;
        sPendingTargetAt = SystemClock.uptimeMillis();
    }

    private static float pendingTarget(long now) {
        if (now - sPendingTargetAt > 300L) return 0f;
        return sPendingTarget;
    }

    /**
     * Tandai animator hasil pabrik animator zoom kamera. Durasi aslinya baru
     * dipasang pemanggil setelah fungsi ini, jadi perapian dilakukan di
     * {@link #hookAnimatorStart()} tepat sebelum {@code start()}.
     */
    private boolean hookZoomAnimFactory(ClassLoader cl, String className, String method) {
        try {
            Class<?> helper = XposedHelpers.findClass(className, cl);
            XposedHelpers.findAndHookMethod(helper, method, float.class, float.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object result = param.getResult();
                            if (result instanceof ValueAnimator) {
                                rememberTarget((Float) param.args[1]);
                                synchronized (ZOOM_ANIMS) {
                                    ZOOM_ANIMS.add((ValueAnimator) result);
                                }
                            }
                        }
                    });
            XposedBridge.log(LOG + ": hook animasi zoom (" + className + "."
                    + method + ") terpasang");
            return true;
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": hook animasi zoom " + className + "." + method
                    + " gagal: " + t);
            return false;
        }
    }

    /** Rapikan durasi/kurva animator zoom yang sudah ditandai, sebelum start(). */
    private void hookAnimatorStart() {
        try {
            XposedHelpers.findAndHookMethod(ValueAnimator.class, "start",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            ValueAnimator anim = (ValueAnimator) param.thisObject;
                            boolean marked;
                            synchronized (ZOOM_ANIMS) {
                                marked = ZOOM_ANIMS.remove(anim);
                            }
                            if (marked) {
                                smoothZoomAnim(anim);
                            }
                        }
                    });
            XposedBridge.log(LOG + ": hook ValueAnimator.start terpasang");
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": hook ValueAnimator.start gagal: " + t);
        }
    }

    /** Ubah durasi/kurva animator zoom sesuai pilihan di aplikasi modul. */
    private static void smoothZoomAnim(ValueAnimator anim) {
        int level = zoomAnimLevel();
        if (level <= 0 || anim == null) return;

        long base = anim.getDuration();
        if (base < 0) base = 0;
        // base == 0 berarti kamera ingin perpindahan instan; beri durasi minimum.
        long duration;
        if (level == 1) {
            duration = clampDuration(Math.round(base * 3.2f), 320L, 620L);
        } else {
            duration = clampDuration(Math.round(base * 5.0f), 550L, 1000L);
        }

        anim.setDuration(duration);
        anim.setInterpolator(new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f));
        beginSmooth(duration, pendingTarget(SystemClock.uptimeMillis()));
        logZoomAnim(base, duration);
    }

    // ------------------------------------------------------------------
    // 0c. Perataan rasio zoom (exponential smoothing)
    // ------------------------------------------------------------------

    /**
     * Kamera 6.7 menggerakkan zoom lewat animator-nya sendiri, tapi kurvanya
     * (gabungan interpolator + spline Vr.j.f) masih menyentak, dan frame
     * pertamanya kadang sedikit mundur. Karena itu modul mengambil nilai akhir
     * animator sebagai target, lalu meramp sendiri posisi zoom dari rasio yang
     * sedang aktif dengan filter eksponensial - gerakannya selalu satu arah.
     *
     * Filter hanya aktif sesaat setelah animator zoom kamera dibuat, supaya
     * pinch-to-zoom tetap mengikuti jari (tidak ikut diperlambat).
     */
    private static final float SMOOTH_EPSILON = 0.0015f;
    private static volatile boolean sSmoothActive;
    private static volatile boolean sSmoothApplying;
    private static volatile float sSmoothTarget;
    private static volatile float sSmoothApplied;
    private static volatile long sSmoothLastAt;
    private static volatile long sSmoothUntil;
    private static volatile float sSmoothTau = 150f;
    private static volatile float sLastRatio = 1f;
    private static volatile Method sZoomSetter;
    private static volatile Object sZoomSetterTarget;
    private static Handler sMainHandler;

    private static Handler mainHandler() {
        Handler h = sMainHandler;
        if (h == null) {
            h = new Handler(Looper.getMainLooper());
            sMainHandler = h;
        }
        return h;
    }

    /** Mulai sesi perataan saat animator zoom kamera akan berjalan. */
    private static void beginSmooth(long durationMs, float target) {
        if (zoomAnimLevel() <= 0) {
            sSmoothActive = false;
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (!(target > 0.3f) || Float.isNaN(target)) {
            // Tidak ada info target baru: jangan ganggu sesi yang sedang jalan.
            return;
        }
        if (sSmoothActive && Math.abs(target - sSmoothTarget) < 0.005f) {
            // Animator tambahan untuk gerakan yang sama: lanjutkan sesi lama.
            return;
        }
        float seed = currentZoomRatio();
        // Tanpa target yang jelas (atau sudah di target), biarkan animator
        // kamera yang sudah diperpanjang berjalan apa adanya.
        if (Math.abs(target - seed) < 0.005f) {
            sSmoothActive = false;
            return;
        }
        sSmoothApplied = seed;
        sSmoothTarget = target;
        sSmoothLastAt = 0L;
        sSmoothTau = Math.max(60f, durationMs * 0.33f);
        sSmoothUntil = now + durationMs + 2500L;
        sSmoothActive = true;
        mainHandler().removeCallbacks(SMOOTH_TICK);
    }

    /**
     * Rasio zoom yang benar-benar aktif saat ini (bukan nilai sementara dari
     * animator). Dipakai sebagai titik awal perataan supaya tidak ada lompatan.
     */
    private static float currentZoomRatio() {
        Object manager = sZoomManager == null ? null : sZoomManager.get();
        if (manager != null) {
            for (String getter : new String[]{"j1", "n0"}) {
                try {
                    float ratio = (Float) XposedHelpers.callMethod(manager, getter);
                    if (ratio > 0f && !Float.isNaN(ratio)) return ratio;
                } catch (Throwable ignored) {
                    // Getter tidak tersedia di versi ROM ini.
                }
            }
        }
        return sLastRatio;
    }

    /**
     * Ganti nilai rasio yang diminta kamera dengan nilai hasil perataan.
     *
     * Selama sesi aktif, nilai frame dari kamera sengaja DIABAIKAN: kamera 6.7
     * kadang mengirim frame pertama yang sedikit mundur dari posisi sekarang
     * (kurva internalnya), dan itu yang terlihat sebagai "zoom out kecil".
     * Yang dipakai hanya target akhir animator, jadi gerakan selalu satu arah.
     */
    private static void smoothZoomRatio(XC_MethodHook.MethodHookParam param) {
        if (sSmoothApplying) return;
        float value = (Float) param.args[0];
        if (!(value > 0f) || Float.isNaN(value)) return;

        long now = SystemClock.uptimeMillis();
        if (zoomAnimLevel() <= 0 || now > sSmoothUntil || !sSmoothActive) {
            if (zoomAnimLevel() <= 0 || now > sSmoothUntil) {
                sSmoothApplied = value;
                sSmoothTarget = value;
            }
            sLastRatio = value;
            return;
        }

        // Pengguna memutar zoom melewati target (pinch): hentikan perataan dan
        // ikuti jari.
        float direction = sSmoothTarget - sSmoothApplied;
        if (direction != 0f) {
            float overshoot = (value - sSmoothTarget) * direction;
            if (overshoot > 0.25f) {
                sSmoothActive = false;
                sSmoothApplied = value;
                sSmoothTarget = value;
                sLastRatio = value;
                return;
            }
        }

        sZoomSetter = (Method) param.method;
        sZoomSetterTarget = param.thisObject;
        float applied = stepSmooth(now);
        sLastRatio = applied;
        param.args[0] = applied;

        mainHandler().removeCallbacks(SMOOTH_TICK);
        mainHandler().postDelayed(SMOOTH_TICK, 16L);
    }

    /** Satu langkah filter eksponensial. */
    private static float stepSmooth(long now) {
        long last = sSmoothLastAt;
        long dt = last == 0L ? 8L : now - last;
        if (dt < 1L) dt = 1L;
        else if (dt > 100L) dt = 100L;
        sSmoothLastAt = now;

        float alpha = (float) (1.0 - Math.exp(-dt / sSmoothTau));
        if (alpha < 0.02f) alpha = 0.02f;
        else if (alpha > 1f) alpha = 1f;

        float next = sSmoothApplied + (sSmoothTarget - sSmoothApplied) * alpha;
        if (Math.abs(sSmoothTarget - next) < SMOOTH_EPSILON) {
            next = sSmoothTarget;
            sSmoothActive = false;
        }
        sSmoothApplied = next;
        return next;
    }

    /** Lanjutkan perataan setelah animator kamera selesai (ekor transisi). */
    private static final Runnable SMOOTH_TICK = new Runnable() {
        @Override
        public void run() {
            if (!sSmoothActive) return;
            Method method = sZoomSetter;
            Object target = sZoomSetterTarget;
            if (method == null || target == null) return;

            float value = stepSmooth(SystemClock.uptimeMillis());
            sLastRatio = value;
            sSmoothApplying = true;
            try {
                method.invoke(target, value);
            } catch (Throwable ignored) {
                sSmoothActive = false;
            } finally {
                sSmoothApplying = false;
            }
            if (sSmoothActive) mainHandler().postDelayed(this, 16L);
        }
    };

    private static long clampDuration(long value, long min, long max) {
        return value < min ? min : Math.min(value, max);
    }

    private static void logZoomAnim(long base, long duration) {
        long now = SystemClock.elapsedRealtime();
        if (now - sLastZoomAnimLogAt < 1500L) return;
        sLastZoomAnimLogAt = now;
        XposedBridge.log(LOG + ": animasi zoom " + base + "ms -> " + duration + "ms");
    }

    // ------------------------------------------------------------------
    // 0. Level zoom kamera (untuk mode Otomatis)
    // ------------------------------------------------------------------

    /**
     * Ambil rasio zoom aktif dari ZoomManager kamera.
     * Dipakai mode Otomatis: bar hitam hanya hilang saat rasio > 1 (mis. 2x),
     * sedangkan di 0.6x / 1x preview tetap 4:3 dengan bar.
     *
     * Implementasinya berpindah antar versi kamera:
     * - 6.7+ : k9.j (method setZoomRatio, d4, qc)
     * - 6.4  : X5.f (method setZoomRatio, e0)
     */
    private void hookZoomManager(ClassLoader cl) {
        XC_MethodHook ratioHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                onZoomRatioChanged(param.thisObject, (Float) param.args[0]);
            }
        };

        if (hookZoomManagerClass(cl, "k9.j", ratioHook, "d4", "qc")
                || hookZoomManagerClass(cl, "X5.f", ratioHook, "e0")) {
            XposedBridge.log(LOG + ": hook ZoomManager terpasang");
        } else {
            XposedBridge.log(LOG + ": hook zoom ratio gagal (mode Otomatis akan pakai 4:3)");
        }
    }

    private static boolean hookZoomManagerClass(ClassLoader cl, String className,
                                                XC_MethodHook hook,
                                                String... extraFloatIntMethods) {
        try {
            Class<?> zoomManager = XposedHelpers.findClass(className, cl);
            // Perataan rasio zoom (exponential smoothing) pada setter utama.
            try {
                XposedHelpers.findAndHookMethod(zoomManager, "setZoomRatio", float.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                smoothZoomRatio(param);
                            }
                        });
            } catch (Throwable t) {
                XposedBridge.log(LOG + ": hook perataan rasio gagal: " + t);
            }
            XposedHelpers.findAndHookMethod(zoomManager, "setZoomRatio", float.class, hook);
            for (String name : extraFloatIntMethods) {
                try {
                    XposedHelpers.findAndHookMethod(zoomManager, name,
                            float.class, int.class, hook);
                } catch (Throwable ignored) {
                    // Method tidak ada di versi ROM ini.
                }
            }
            XposedBridge.log(LOG + ": hook ZoomManager (" + className + ") terpasang");
            return true;
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": hook ZoomManager " + className + " gagal: " + t);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 1. Top bar transparan
    // ------------------------------------------------------------------

    private void hookShapeBackGroundView(ClassLoader cl) {
        // Kamera 6.7+: class pindah ke paket halo, method penyiapannya b().
        // Kamera 6.4 : com.android.camera.ui.ShapeBackGroundView.d().
        if (!hookShapeClass(cl, "com.xiaomi.camera.ui.base.halo.ShapeBackGroundView", "b")) {
            hookShapeClass(cl, "com.android.camera.ui.ShapeBackGroundView", "d");
        }
    }

    private static boolean hookShapeClass(ClassLoader cl, String className, String method) {
        try {
            Class<?> shape = XposedHelpers.findClass(className, cl);

            XposedHelpers.findAndHookMethod(shape, method, new XC_MethodHook() {
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
            XposedBridge.log(LOG + ": hook ShapeBackGroundView." + method + "() terpasang");
            return true;
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": hook " + className + "." + method + " gagal: " + t);
            return false;
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

        int corner = cornerDp();
        int cornerPx = corner <= 0 ? 0
                : Math.round(corner * decor.getResources().getDisplayMetrics().density);
        applyCornerRadius(surface, cornerPx);

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
        float fillScale;
        float centerY;
        if (contentHeight <= 0f || contentHeight > h * 0.98f) {
            // Sudah full-screen / rasio tidak diketahui: jangan diubah.
            fillScale = 1f;
            centerY = h / 2f;
        } else {
            fillScale = h / contentHeight;
            centerY = (contentTop + contentBottom) / 2f;
        }

        float fraction = 0f;
        if (fillScale > 1.001f) {
            int mode = zoomMode();
            if (mode == MODE_AUTO) {
                // Otomatis: bar hitam hanya hilang saat preview benar-benar di-zoom.
                fraction = autoFill() ? 1f : 0f;
            } else if (mode > 0) {
                // 50% = separuh gap ditutup, jadi crop dan upscale-nya lebih ringan.
                fraction = mode == 1 ? 0.5f : 1f;
            }
        }

        animateZoom(surface, fraction, fillScale, w, h, centerY);
    }

    /** Durasi transisi preview supaya perpindahan bar hitam <-> penuh tidak "patah". */
    private static final long ZOOM_ANIM_MS = 220L;

    /** Status animasi per SurfaceView: target terakhir dan animator yang berjalan. */
    private static final WeakHashMap<SurfaceView, ZoomState> ZOOM_STATES = new WeakHashMap<>();

    private static final class ZoomState {
        /** Nilai yang sedang ditampilkan (0 = 4:3 dengan bar, 1 = penuh layar). */
        float fraction;
        /** Nilai tujuan terakhir. */
        float target;
        ValueAnimator animator;
    }

    /**
     * Terapkan skala preview dengan animasi halus. Target yang sama tidak
     * mengulang animasi, jadi aman dipanggil dari global layout listener.
     */
    private static void animateZoom(final SurfaceView surface, float targetFraction,
                                    float fillScale, int w, int h, float centerY) {
        float safe = targetFraction < 0f ? 0f : Math.min(targetFraction, 1f);

        ZoomState state = ZOOM_STATES.get(surface);
        if (state == null) {
            // View pertama kali dipakai (kamera baru dibuka): pasang langsung.
            state = new ZoomState();
            state.fraction = safe;
            state.target = safe;
            ZOOM_STATES.put(surface, state);
            setZoomTransforms(surface, safe, fillScale, w, h, centerY);
            return;
        }

        if (Math.abs(state.target - safe) < 0.001f) {
            // Target tidak berubah: biarkan animasi berjalan, atau pastikan
            // posisi akhir tetap benar setelah layout berubah.
            if (state.animator == null || !state.animator.isRunning()) {
                setZoomTransforms(surface, safe, fillScale, w, h, centerY);
            }
            return;
        }

        state.target = safe;
        ValueAnimator previous = state.animator;
        if (previous != null) {
            // Null-kan dulu supaya callback cancel() animasi lama tidak
            // melompatkan tampilan ke target baru.
            state.animator = null;
            previous.cancel();
        }

        final ZoomState s = state;
        ValueAnimator anim = ValueAnimator.ofFloat(s.fraction, safe);
        anim.setDuration(ZOOM_ANIM_MS);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            s.fraction = (Float) a.getAnimatedValue();
            setZoomTransforms(surface, s.fraction, fillScale, w, h, centerY);
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (s.animator != animation) return;  // sudah diganti animasi baru
                s.fraction = s.target;
                s.animator = null;
                setZoomTransforms(surface, s.fraction, fillScale, w, h, centerY);
            }
        });
        s.animator = anim;
        anim.start();
    }

    /** Pivot di tengah area konten; translationY ikut diinterpolasi agar mulus. */
    private static void setZoomTransforms(SurfaceView surface, float fraction,
                                          float fillScale, int w, int h, float centerY) {
        float scale = 1f + (fillScale - 1f) * fraction;
        surface.setPivotX(w / 2f);
        surface.setPivotY(centerY);
        surface.setScaleX(scale);
        surface.setScaleY(scale);
        surface.setTranslationY((h / 2f - centerY) * fraction);
    }

    /** Radius sudut yang sudah dipasang per SurfaceView (0 = kotak). */
    private static final WeakHashMap<SurfaceView, Integer> CORNER_RADII = new WeakHashMap<>();

    /**
     * Bulatkan sudut preview. Memakai SurfaceView.setCornerRadius (hidden, ada sejak
     * Android 12) yang membulatkan surface sekaligus lubang view-nya, jadi tetap
     * benar walau preview di-scale. Gagal = sudut tetap kotak, kamera tidak rusak.
     */
    private static void applyCornerRadius(SurfaceView surface, int radiusPx) {
        Integer previous = CORNER_RADII.get(surface);
        if (previous != null && previous.intValue() == radiusPx) return;
        CORNER_RADII.put(surface, radiusPx);

        try {
            XposedHelpers.callMethod(surface, "setCornerRadius", (float) radiusPx);
            surface.invalidate();
            if (radiusPx > 0) {
                XposedBridge.log(LOG + ": sudut preview = " + radiusPx + "px");
            }
        } catch (Throwable t) {
            XposedBridge.log(LOG + ": sudut preview gagal (setCornerRadius): " + t);
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
                    int corner = extras.getInt(ConfigReceiver.KEY_CORNER, DEFAULT_CORNER);
                    int zoomAnim = extras.getInt(ConfigReceiver.KEY_ZOOM_ANIM, DEFAULT_ZOOM_ANIM);
                    zoom = zoom < 0 ? 0 : Math.min(zoom, MODE_AUTO);
                    scrim = scrim <= 0 ? 0 : Math.min(scrim, 50);
                    corner = corner <= 0 ? 0 : Math.min(corner, 60);
                    zoomAnim = zoomAnim <= 0 ? 0 : Math.min(zoomAnim, 2);

                    if (sQueryFailed) {
                        sQueryFailed = false;
                        XposedBridge.log(LOG + ": pengaturan modul terbaca lagi");
                    }

                    if (zoom != sLastZoomMode || scrim != sLastScrim
                            || corner != sLastCorner) {
                        sLastZoomMode = zoom;
                        sLastScrim = scrim;
                        sLastCorner = corner;
                        XposedBridge.log(LOG + ": config zoom=" + zoom + " scrim=" + scrim
                                + "% sudut=" + corner + "dp");
                        reapplyLast();
                    }

                    if (zoomAnim != sLastZoomAnim) {
                        sLastZoomAnim = zoomAnim;
                        XposedBridge.log(LOG + ": config transisiZoom=" + zoomAnim);
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

    private static int cornerDp() {
        return sLastCorner < 0 ? DEFAULT_CORNER : sLastCorner;
    }

    private static int zoomAnimLevel() {
        return sLastZoomAnim < 0 ? DEFAULT_ZOOM_ANIM : sLastZoomAnim;
    }

    /**
     * Mode Otomatis: preview dianggap "sudah di-zoom" kalau rasio > 1,05x.
     * Pakai histeresis: setelah penuh layar, baru kembali 4:3 di bawah 1,02x
     * supaya tidak berkedip saat jari berhenti tepat di batas ambang.
     */
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
        float threshold = sAutoFilled ? AUTO_ZOOM_RELEASE : AUTO_ZOOM_THRESHOLD;
        sAutoFilled = sZoomRatio > threshold;
        return sAutoFilled;
    }

    /** Dipanggil dari hook ZoomManager saat rasio zoom berubah. */
    private static void onZoomRatioChanged(Object manager, Float ratio) {
        if (manager != null) sZoomManager = new WeakReference<>(manager);

        float value = ratio == null || Float.isNaN(ratio) ? 0f : ratio;
        // Utamakan nilai yang benar-benar tersimpan di ZoomManager (bisa saja
        // argumen hook masih nilai mentah sebelum di-clamp).
        // Getter: n0() di kamera 6.4, j1() di kamera 6.7+.
        if (manager != null) {
            for (String getter : new String[]{"n0", "j1"}) {
                try {
                    float committed = (Float) XposedHelpers.callMethod(manager, getter);
                    if (committed > 0f && !Float.isNaN(committed)) {
                        value = committed;
                        break;
                    }
                } catch (Throwable ignored) {
                    // Getter tidak tersedia di versi ROM ini.
                }
            }
        }
        if (value <= 0f) return;

        sZoomRatio = value;
        float threshold = sAutoFilled ? AUTO_ZOOM_RELEASE : AUTO_ZOOM_THRESHOLD;
        boolean nowFill = value > threshold;
        if (nowFill == sAutoFilled) return;

        sAutoFilled = nowFill;
        if (zoomMode() != MODE_AUTO) return;

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
