package py.crz.flowlauncher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Utilidades de interfaz pensadas para el control remoto. */
final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(int color, float radius, int strokePx, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    static Drawable background(LauncherTheme t) {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{t.bgTop, t.bgBottom});
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    static TextView sectionTitle(Context c, LauncherTheme t, String s) {
        TextView tv = text(c, s.toUpperCase(), 14, t.accent, true);
        tv.setLetterSpacing(0.08f);
        tv.setPadding(0, dp(c, 22), 0, dp(c, 4));
        return tv;
    }

    static void focusAnim(View v, boolean focused, float scale) {
        v.animate().scaleX(focused ? scale : 1f).scaleY(focused ? scale : 1f).setDuration(140).start();
        v.setTranslationZ(focused ? dp(v.getContext(), 6) : 0);
    }

    /** Opacidad de las apps NO seleccionadas según el efecto elegido. */
    static float restAlpha(int effect) {
        return effect == 0 ? 1f : effect == 2 ? 0.5f : 0.72f;
    }

    /**
     * Efecto "pro" al seleccionar una app: las demás quedan atenuadas, la elegida se ilumina,
     * crece con un pequeño rebote y proyecta un brillo del color del tema.
     */
    static void tileFocus(View tile, View card, boolean f, int accent, int effect) {
        float scale = effect == 2 ? 1.12f : 1.10f;
        tile.animate()
                .scaleX(f ? scale : 1f).scaleY(f ? scale : 1f)
                .alpha(f ? 1f : restAlpha(effect))
                .setDuration(f ? 260 : 180)
                .setInterpolator(f ? new android.view.animation.OvershootInterpolator(1.6f)
                        : new android.view.animation.DecelerateInterpolator())
                .start();
        tile.setTranslationZ(f ? dp(tile.getContext(), 10) : 0);
        card.setElevation(f ? dp(card.getContext(), 14) : 0);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            card.setOutlineAmbientShadowColor(accent);
            card.setOutlineSpotShadowColor(accent);
        }
    }

    /** Fila grande de menú (título + subtítulo) que se resalta al seleccionarla. */
    static LinearLayout row(Context c, LauncherTheme t, String title, String sub, Runnable action) {
        final LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        int ph = dp(c, 22), pv = dp(c, 12);
        r.setPadding(ph, pv, ph, pv);
        final float rad = dp(c, 12);
        r.setBackground(rounded(t.card, rad, 0, 0));
        final TextView tt = text(c, title, 18, t.text, true);
        r.addView(tt);
        final TextView st = sub == null ? null : text(c, sub, 14, t.subtext, false);
        if (st != null) r.addView(st);
        r.setOnFocusChangeListener((v, f) -> {
            r.setBackground(rounded(f ? t.accent : t.card, rad, 0, 0));
            tt.setTextColor(f ? t.onAccent : t.text);
            if (st != null) st.setTextColor(f ? t.onAccent : t.subtext);
            focusAnim(r, f, 1.02f);
        });
        r.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        r.setLayoutParams(lp);
        return r;
    }

    /** Botón tipo píldora. */
    static TextView button(Context c, LauncherTheme t, String label, Runnable action) {
        final TextView b = text(c, label, 15, t.text, true);
        b.setFocusable(true);
        b.setClickable(true);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        int ph = dp(c, 18), pv = dp(c, 9);
        b.setPadding(ph, pv, ph, pv);
        final float rad = dp(c, 24);
        b.setBackground(rounded(t.card, rad, 0, 0));
        b.setOnFocusChangeListener((v, f) -> {
            b.setBackground(rounded(f ? t.accent : t.card, rad, 0, 0));
            b.setTextColor(f ? t.onAccent : t.text);
            focusAnim(b, f, 1.06f);
        });
        b.setOnClickListener(v -> {
            if (action != null) action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(c, 10);
        b.setLayoutParams(lp);
        return b;
    }

    @SuppressWarnings("deprecation")
    static void immersive(Activity a) {
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    static void toast(Context c, String s) {
        Toast.makeText(c.getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    static String formatBytes(long b) {
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.0f MB", mb);
        return String.format("%.1f GB", mb / 1024.0);
    }
}
