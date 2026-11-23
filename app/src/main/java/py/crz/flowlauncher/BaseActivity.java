package py.crz.flowlauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Base para las pantallas de menú (Ajustes, Mantenimiento). */
abstract class BaseActivity extends Activity {
    Prefs prefs;
    LauncherTheme t;
    FrameLayout root;
    ScrollView scroll;
    LinearLayout body;
    final List<View> rows = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        t = LauncherTheme.get(prefs.theme());
        root = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        int h = Ui.dp(this, 64), v = Ui.dp(this, 28);
        body.setPadding(h, v, h, v);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        t = LauncherTheme.get(prefs.theme());
        root.setBackground(Ui.background(t));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(this);
    }

    /** Índice de la fila que tiene el foco ahora (para restaurarlo al redibujar). */
    int focusedRow() {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).hasFocus()) return i;
        return 0;
    }

    void clear() {
        body.removeAllViews();
        rows.clear();
    }

    void header(String title, String subtitle) {
        TextView tt = Ui.text(this, title, 32, t.text, true);
        body.addView(tt);
        if (subtitle != null) {
            TextView st = Ui.text(this, subtitle, 15, t.subtext, false);
            st.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 4));
            body.addView(st);
        }
    }

    void section(String name) {
        body.addView(Ui.sectionTitle(this, t, name));
    }

    View row(String title, String sub, Runnable action) {
        View r = Ui.row(this, t, title, sub, action);
        body.addView(r);
        rows.add(r);
        return r;
    }

    void focusRow(int index) {
        if (rows.isEmpty()) return;
        final View v = rows.get(Math.max(0, Math.min(index, rows.size() - 1)));
        v.post(v::requestFocus);
    }
}
