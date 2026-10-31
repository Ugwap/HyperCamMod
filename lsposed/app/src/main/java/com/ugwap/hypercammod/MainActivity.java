package com.ugwap.hypercammod;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Layar informasi sederhana. Modul ini tidak punya setting;
 * semua efek dijalankan oleh HookEntry di proses kamera.
 */
public class MainActivity extends Activity {

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
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Scrim gradient ala iOS untuk kamera HyperOS");
        subtitle.setTextColor(0xFFFFB300);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setPadding(0, Math.round(4 * dp), 0, pad);
        box.addView(subtitle);

        box.addView(body(
                "Modul ini menambah gradient hitam di area atas dan bawah kamera bawaan "
                        + "(com.android.camera) serta membuat background top bar transparan. "
                        + "Logika kamera, foto, dan video tidak diubah.",
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

    private TextView header(String text, float dp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
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
