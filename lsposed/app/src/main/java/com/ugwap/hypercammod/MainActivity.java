package com.ugwap.hypercammod;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Layar informasi + satu pengaturan: sembunyikan ikon dari home screen.
 * Semua efek kamera dijalankan oleh HookEntry di proses com.android.camera.
 */
public class MainActivity extends Activity {

    /** Nama activity-alias yang memegang intent LAUNCHER di manifest. */
    private static final String LAUNCHER_ALIAS = "com.ugwap.hypercammod.Launcher";

    private Switch hideSwitch;
    /** Mencegah listener terpanggil saat mengembalikan posisi switch. */
    private boolean suppressListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float dp = getResources().getDisplayMetrics().density;
        int pad = Math.round(20 * dp);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101010);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("HyperCamMod");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Preview full-screen + scrim untuk kamera HyperOS");
        subtitle.setTextColor(0xFFFFB300);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setPadding(0, Math.round(4 * dp), 0, pad);
        box.addView(subtitle);

        box.addView(body(
                "Modul ini membuat preview kamera bawaan (com.android.camera) menutup "
                        + "seluruh layar dan menambahkan scrim gradient hitam 50% di area "
                        + "atas/bawah. Logika foto dan video tidak diubah.",
                dp));

        box.addView(header("Cara mengaktifkan", dp));

        box.addView(body(
                "1. Buka LSPosed Manager.\n"
                        + "2. Aktifkan modul HyperCamMod.\n"
                        + "3. Pastikan scope-nya mencentang Kamera (com.android.camera).\n"
                        + "4. Force stop aplikasi Kamera, lalu buka lagi.\n"
                        + "5. Kalau belum berubah, reboot HP.",
                dp));

        box.addView(header("Kalau tidak ada efek", dp));

        box.addView(body(
                "Pastikan LSPosed (Zygisk) aktif dan modul ini tidak dalam keadaan mati. "
                        + "Cek log LSPosed dengan filter tag \"HyperCamMod\" untuk melihat pesan hook.",
                dp));

        box.addView(header("Ikon aplikasi", dp));

        hideSwitch = new Switch(this);
        hideSwitch.setText("Sembunyikan ikon dari home screen");
        hideSwitch.setTextColor(Color.WHITE);
        hideSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        hideSwitch.setPadding(0, Math.round(8 * dp), 0, Math.round(4 * dp));
        hideSwitch.setChecked(isLauncherHidden());
        hideSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (!suppressListener) setLauncherHidden(checked);
        });
        box.addView(hideSwitch);

        box.addView(body(
                "Ikon hanya disembunyikan dari launcher. Modul tetap aktif dan tetap "
                        + "muncul di daftar LSPosed Manager — tombol buka/setelan di sana "
                        + "tetap berfungsi karena memakai category khusus "
                        + "(MODULE_SETTINGS).\n\n"
                        + "Kalau perlu jalan lain, dari PC:\n\n"
                        + "adb shell am start -n com.ugwap.hypercammod/.MainActivity",
                dp));

        Button open = new Button(this);
        open.setText("Buka Kamera");
        open.setOnClickListener(v -> {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.android.camera", "com.android.camera.Camera");
                startActivity(intent);
            } catch (Throwable t) {
                Toast.makeText(this, "Kamera tidak bisa dibuka: " + t, Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(24 * dp);
        box.addView(open, lp);

        TextView footer = new TextView(this);
        footer.setText("github.com/Ugwap/HyperCamMod");
        footer.setTextColor(0xFF808080);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(0, pad, 0, 0);
        box.addView(footer);

        scroll.addView(box);
        setContentView(scroll);
    }

    private ComponentName launcherAlias() {
        return new ComponentName(this, LAUNCHER_ALIAS);
    }

    private boolean isLauncherHidden() {
        int state = getPackageManager().getComponentEnabledSetting(launcherAlias());
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
    }

    private void setLauncherHidden(boolean hidden) {
        try {
            getPackageManager().setComponentEnabledSetting(
                    launcherAlias(),
                    hidden
                            ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP);
            Toast.makeText(
                    this,
                    hidden
                            ? "Ikon disembunyikan dari home screen"
                            : "Ikon ditampilkan lagi di home screen",
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "Gagal mengubah ikon: " + t, Toast.LENGTH_LONG).show();
            suppressListener = true;
            hideSwitch.setChecked(!hidden);
            suppressListener = false;
        }
    }

    private TextView header(String text, float dp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        view.setTypeface(view.getTypeface(), Typeface.BOLD);
        view.setPadding(0, Math.round(20 * dp), 0, Math.round(6 * dp));
        return view;
    }

    private TextView body(String text, float dp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xFFCCCCCC);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setLineSpacing(Math.round(2 * dp), 1f);
        return view;
    }
}
