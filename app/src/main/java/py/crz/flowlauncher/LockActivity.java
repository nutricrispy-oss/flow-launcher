package py.crz.flowlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Pantalla de bloqueo: muestra fondo y hora; se desbloquea con el PIN. */
public class LockActivity extends Activity {

    private Prefs prefs;
    private TextView clock, date;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean asking;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now));
            String d = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "PY")).format(now);
            if (!d.isEmpty()) d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
            date.setText(d);
            handler.postDelayed(this, 10000);
        }
    };

    /** Bloquea el equipo y muestra esta pantalla. */
    static void lock(Context c) {
        new Prefs(c).setScreenLocked(true);
        Intent i = new Intent(c, LockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        c.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        LauncherTheme t = LauncherTheme.get(prefs.theme());

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background(t));
        int src = Wallpaper.resolve(this, prefs);
        Bitmap bm = src == -2 ? Wallpaper.load(this, 1920) : src >= 0 ? Wallpaper.loadBuiltin(this, src, 1920) : null;
        if (bm != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(bm);
            root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        }
        View dim = new View(this);
        dim.setBackgroundColor(0x88000000);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView lock = Ui.text(this, "🔒", 40, Color.WHITE, false);
        lock.setGravity(Gravity.CENTER);
        clock = Ui.text(this, "", 96, Color.WHITE, false);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setGravity(Gravity.CENTER);
        date = Ui.text(this, "", 22, 0xDDFFFFFF, false);
        date.setGravity(Gravity.CENTER);
        TextView hint = Ui.text(this, "Equipo bloqueado  ·  Presioná OK para desbloquear", 16, 0xCCFFFFFF, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(this, 28), 0, 0);
        box.addView(lock);
        box.addView(clock);
        box.addView(date);
        box.addView(hint);
        root.addView(box, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        if (!prefs.screenLocked()) {
            finish();
            return;
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(this);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        // El volumen y el silencio siguen funcionando
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN
                || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(e);
        }
        if (e.getAction() == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_DPAD_CENTER
                || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER
                || k == KeyEvent.KEYCODE_BUTTON_A)) {
            unlock();
        }
        return true; // todos los demás botones quedan bloqueados
    }

    @Override
    public void onBackPressed() {
        // Bloqueado: Atrás no hace nada
    }

    private void unlock() {
        if (asking) return;
        if (!prefs.hasPin()) {
            done();
            return;
        }
        asking = true;
        PinDialogs.ask(this, "PIN para desbloquear", pin -> {
            asking = false;
            if (prefs.checkPin(pin)) done();
            else Ui.toast(this, "PIN incorrecto");
        });
        // Si se cancela el diálogo, permitir volver a intentar
        handler.postDelayed(() -> asking = false, 1500);
    }

    private void done() {
        prefs.setScreenLocked(false);
        finish();
    }
}
