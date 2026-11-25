package py.crz.flowlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pantalla de inicio del launcher. */
public class HomeActivity extends Activity {

    /** Se pone en true cuando hay que volver a leer las apps instaladas. */
    static volatile boolean needsReload = true;
    /** Se pone en true al cambiar la ciudad del clima. */
    static volatile boolean weatherDirty = false;
    /** Se pone en true para volver a leer «seguir-viendo.txt» de GitHub. */
    static volatile boolean continueDirty = false;

    private Prefs prefs;
    private LauncherTheme t;
    private FrameLayout root;
    private ImageView wallImg;
    private View dimView;
    private ScrollView scroll;
    private LinearLayout content, buttonBox, favRow;
    private GridLayout grid;
    private TextView clock, ampm, date, net, favTitle, allTitle, recTitle;
    private LinearLayout clockBox, weatherBox, recRow;
    private HorizontalScrollView recScroll, cwScroll;
    private LinearLayout cwRow;
    private TextView cwTitle;
    private AnalogClockView analog;
    private TextView btButton;
    private long lastBackUp;
    private TextView wIcon, wTemp, wDetail;
    private int builtClockStyle = -1;
    private long lastClean, lastWeatherTry;

    private final List<AppItem> apps = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String lastFocusTag;
    private String wallKey = "";
    private boolean loading;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            needsReload = true;
            reload();
        }
    };

    private final BroadcastReceiver systemReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
            updateNet();
            updateBtLabel();
        }
    };

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            int st = prefs.clockStyle();
            handler.postDelayed(this, (st == 1 || st == 3) ? 1000 : 15000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        t = LauncherTheme.get(prefs.theme());
        buildUi();
        registerReceivers();
    }

    // ------------------------------------------------------------------ UI base

    private void buildUi() {
        root = new FrameLayout(this);
        wallImg = new ImageView(this);
        wallImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallImg, new FrameLayout.LayoutParams(-1, -1));
        dimView = new View(this);
        root.addView(dimView, new FrameLayout.LayoutParams(-1, -1));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipChildren(false);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(Ui.dp(this, 48), Ui.dp(this, 24), Ui.dp(this, 48), Ui.dp(this, 48));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // Encabezado: hora, fecha, red y botones
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.HORIZONTAL);
        clockBox.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(clockBox);
        LinearLayout dc = new LinearLayout(this);
        dc.setOrientation(LinearLayout.VERTICAL);
        dc.setPadding(Ui.dp(this, 16), 0, 0, 0);
        date = Ui.text(this, "", 15, Color.WHITE, false);
        net = Ui.text(this, "", 12, Color.WHITE, false);
        dc.addView(date);
        dc.addView(net);
        header.addView(dc);

        // Clima
        weatherBox = new LinearLayout(this);
        weatherBox.setOrientation(LinearLayout.HORIZONTAL);
        weatherBox.setGravity(Gravity.CENTER_VERTICAL);
        weatherBox.setPadding(Ui.dp(this, 24), 0, 0, 0);
        wIcon = Ui.text(this, "", 24, Color.WHITE, false);
        weatherBox.addView(wIcon);
        LinearLayout wc = new LinearLayout(this);
        wc.setOrientation(LinearLayout.VERTICAL);
        wc.setPadding(Ui.dp(this, 8), 0, 0, 0);
        wTemp = Ui.text(this, "", 16, Color.WHITE, true);
        wDetail = Ui.text(this, "", 11, Color.WHITE, false);
        wc.addView(wTemp);
        wc.addView(wDetail);
        weatherBox.addView(wc);
        weatherBox.setVisibility(View.GONE);
        header.addView(weatherBox);
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        buttonBox = new LinearLayout(this);
        buttonBox.setOrientation(LinearLayout.HORIZONTAL);
        buttonBox.setClipChildren(false);
        header.addView(buttonBox);
        content.addView(header);

        favTitle = Ui.text(this, "FAVORITOS", 14, Color.WHITE, true);
        favTitle.setLetterSpacing(0.08f);
        favTitle.setPadding(Ui.dp(this, 10), Ui.dp(this, 20), 0, 0);
        content.addView(favTitle);

        HorizontalScrollView favScroll = new HorizontalScrollView(this);
        favScroll.setHorizontalScrollBarEnabled(false);
        favScroll.setClipChildren(false);
        favScroll.setClipToPadding(false);
        favRow = new LinearLayout(this);
        favRow.setOrientation(LinearLayout.HORIZONTAL);
        favRow.setClipChildren(false);
        favRow.setClipToPadding(false);
        favRow.setPadding(0, Ui.dp(this, 12), Ui.dp(this, 24), Ui.dp(this, 12));
        favScroll.addView(favRow);
        content.addView(favScroll);

        recTitle = Ui.text(this, "RECIENTES", 14, Color.WHITE, true);
        recTitle.setLetterSpacing(0.08f);
        recTitle.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), 0, 0);
        content.addView(recTitle);
        recScroll = new HorizontalScrollView(this);
        recScroll.setHorizontalScrollBarEnabled(false);
        recScroll.setClipChildren(false);
        recScroll.setClipToPadding(false);
        recRow = new LinearLayout(this);
        recRow.setOrientation(LinearLayout.HORIZONTAL);
        recRow.setClipChildren(false);
        recRow.setClipToPadding(false);
        recRow.setPadding(0, Ui.dp(this, 12), Ui.dp(this, 24), Ui.dp(this, 12));
        recScroll.addView(recRow);
        content.addView(recScroll);

        cwTitle = Ui.text(this, "SEGUIR VIENDO", 14, Color.WHITE, true);
        cwTitle.setLetterSpacing(0.08f);
        cwTitle.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), 0, 0);
        content.addView(cwTitle);
        cwScroll = new HorizontalScrollView(this);
        cwScroll.setHorizontalScrollBarEnabled(false);
        cwScroll.setClipChildren(false);
        cwScroll.setClipToPadding(false);
        cwRow = new LinearLayout(this);
        cwRow.setOrientation(LinearLayout.HORIZONTAL);
        cwRow.setClipChildren(false);
        cwRow.setClipToPadding(false);
        cwRow.setPadding(0, Ui.dp(this, 12), Ui.dp(this, 24), Ui.dp(this, 12));
        cwScroll.addView(cwRow);
        content.addView(cwScroll);

        allTitle = Ui.text(this, "TODAS LAS APLICACIONES", 14, Color.WHITE, true);
        allTitle.setLetterSpacing(0.08f);
        allTitle.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), 0, 0);
        content.addView(allTitle);

        grid = new GridLayout(this);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        content.addView(grid);

        setContentView(root);
    }

    private void registerReceivers() {
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        register(packageReceiver, pf);

        IntentFilter sf = new IntentFilter();
        sf.addAction(Intent.ACTION_TIME_TICK);
        sf.addAction(Intent.ACTION_TIME_CHANGED);
        sf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        sf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        sf.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED);
        sf.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED);
        sf.addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED);
        register(systemReceiver, sf);
    }

    private void register(BroadcastReceiver r, IntentFilter f) {
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, 2 /* RECEIVER_EXPORTED */);
            else registerReceiver(r, f);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(packageReceiver);
            unregisterReceiver(systemReceiver);
        } catch (Exception ignored) {
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    /** Bloquea el equipo. Si no hay PIN, primero pide crearlo (sin PIN cualquiera podría desbloquear). */
    private void requestLock() {
        if (prefs.hasPin()) {
            LockActivity.lock(this);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Bloquear la Flow Box")
                .setMessage("Para que el bloqueo sirva, primero creá un PIN. Con ese PIN se desbloquea.")
                .setPositiveButton("Crear PIN", (d, w) -> PinDialogs.create(this, prefs, () -> LockActivity.lock(this)))
                .setNeutralButton("Bloquear sin PIN", (d, w) -> LockActivity.lock(this))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.screenLocked()) {
            // Sigue bloqueado (por ejemplo, después de apretar Inicio o de reiniciar)
            LockActivity.lock(this);
            return;
        }
        Ui.immersive(this);
        applyTheme();
        updateClock();
        updateNet();
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        if (needsReload || apps.isEmpty()) reload();
        else render();
        maybeShowHomeHint();
        updateWeather(weatherDirty);
        weatherDirty = false;
        syncContinue();
        autoClean();
        autoUpdateCheck();
    }

    // ------------------------------------------------------------------ Tareas automáticas

    /** Lee «seguir-viendo.txt» de GitHub cada 15 minutos (o al pedirlo desde Ajustes). */
    private void syncContinue() {
        if (!prefs.showContinue()) return;
        long now = System.currentTimeMillis();
        if (!continueDirty && now - prefs.cwLastSync() < 15 * 60 * 1000) return;
        continueDirty = false;
        prefs.setCwLastSync(now);
        final Context app = getApplicationContext();
        final String repo = prefs.fondosRepo();
        final String file = prefs.cwFile();
        try {
            executor.execute(() -> {
                ContinueWatching.sync(app, repo, file);
                runOnUiThread(() -> {
                    if (!isFinishing()) render();
                });
            });
        } catch (Exception ignored) {
        }
    }

    private View makeContinueTile(final ContinueWatching.Entry e, int index, int w, int h) {
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setClipChildren(false);
        tile.setTag("cw:" + index);
        final float r = Ui.dp(this, 10);
        final FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.rounded(t.card, r, 0, 0));
        card.setClipToOutline(true);
        File thumbFile = ContinueWatching.thumb(this, e);
        Bitmap thumb = thumbFile.exists() ? Wallpaper.decodeFile(thumbFile, 480) : null;
        android.graphics.drawable.Drawable appIcon = ContinueWatching.appIcon(this, e);
        if (thumb != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(thumb);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            if (appIcon != null) {
                ImageView badge = new ImageView(this);
                badge.setImageDrawable(appIcon);
                int bs = Ui.dp(this, 24);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bs, bs, Gravity.BOTTOM | Gravity.START);
                lp.setMargins(Ui.dp(this, 6), 0, 0, Ui.dp(this, 6));
                card.addView(badge, lp);
            }
        } else {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (appIcon != null) iv.setImageDrawable(appIcon);
            else iv.setImageResource(android.R.drawable.ic_media_play);
            int s = Math.round(h * 0.5f);
            card.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }
        TextView play = Ui.text(this, "▶", 14, Color.WHITE, true);
        play.setPadding(Ui.dp(this, 7), Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2));
        play.setBackground(Ui.rounded(0x99000000, Ui.dp(this, 8), 0, 0));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        plp.setMargins(0, Ui.dp(this, 6), Ui.dp(this, 6), 0);
        card.addView(play, plp);
        tile.addView(card, new LinearLayout.LayoutParams(w, h));

        final TextView label = Ui.text(this, e.name, 14, t.subtext, false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setPadding(0, Ui.dp(this, 6), 0, 0);
        tile.addView(label, new LinearLayout.LayoutParams(w, -2));

        tile.setOnFocusChangeListener((v, f) -> {
            Ui.focusAnim(tile, f, 1.08f);
            card.setForeground(f ? Ui.rounded(Color.TRANSPARENT, r, Ui.dp(this, 3), t.accent) : null);
            label.setTextColor(f ? t.text : t.subtext);
            label.setTypeface(f ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            if (f) lastFocusTag = (String) tile.getTag();
        });
        tile.setOnClickListener(v -> {
            if (!ContinueWatching.open(this, e)) Ui.toast(this, "Ninguna app de este equipo abre ese link");
        });
        tile.setOnLongClickListener(v -> {
            Ui.toast(this, "Para quitar o cambiar este acceso, editá «" + prefs.cwFile() + "» en GitHub");
            return true;
        });
        return tile;
    }

    private void autoClean() {
        if (!prefs.autoClean()) return;
        long now = System.currentTimeMillis();
        if (now - lastClean < 60000) return;
        lastClean = now;
        final Context app = getApplicationContext();
        try {
            executor.execute(() -> Cleaner.killBackground(app));
        } catch (Exception ignored) {
        }
    }

    private void autoUpdateCheck() {
        if (!Updater.shouldAutoCheck(this, prefs)) return;
        prefs.setLastUpdateCheck(System.currentTimeMillis());
        final int current = Updater.currentVersionCode(this);
        try {
            executor.execute(() -> {
                final Updater.Release r = Updater.latest();
                if (r == null || r.versionCode <= current) return;
                runOnUiThread(() -> {
                    if (!isFinishing() && hasWindowFocus()) Updater.offer(this, r);
                });
            });
        } catch (Exception ignored) {
        }
    }

    private void showWeather(Weather.Now n) {
        if (n == null) return;
        wIcon.setText(Weather.icon(n.code));
        wTemp.setText(n.temp + "°  " + Weather.text(n.code));
        wDetail.setText(n.city + "  ·  máx " + n.max + "°  mín " + n.min + "°");
        weatherBox.setVisibility(View.VISIBLE);
    }

    /** Muestra el clima guardado y lo actualiza cada 30 minutos. */
    private void updateWeather(boolean force) {
        if (!prefs.showWeather()) {
            weatherBox.setVisibility(View.GONE);
            return;
        }
        Weather.Now n = Weather.cached(this);
        showWeather(n);
        long now = System.currentTimeMillis();
        boolean stale = n == null || now - n.time > 30 * 60 * 1000;
        if (!force && (!stale || now - lastWeatherTry < 5 * 60 * 1000)) return;
        lastWeatherTry = now;
        final Context app = getApplicationContext();
        try {
            executor.execute(() -> {
                try {
                    final Weather.Now fresh = Weather.fetch(app);
                    runOnUiThread(() -> {
                        if (!isFinishing() && prefs.showWeather()) showWeather(fresh);
                    });
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    /** Si el equipo no deja usar Flow Launcher como predeterminado, ofrece activar el guardián. */
    private void maybeShowHomeHint() {
        if (prefs.homeHintShown() || HomeGuardService.isDefaultHome(this)
                || HomeGuardService.isEnabled(this)) return;
        prefs.setHomeHintShown(true);
        handler.postDelayed(() -> {
            if (isFinishing()) return;
            new AlertDialog.Builder(this)
                    .setTitle("Que Flow Launcher sea siempre el inicio")
                    .setMessage("Este equipo no deja elegir otro launcher predeterminado.\n\n"
                            + "Solución: activá «Flow Launcher - Volver al inicio» en Accesibilidad. "
                            + "Así, cada vez que presiones Inicio o Salir, el equipo vuelve a Flow Launcher.\n\n"
                            + "Ruta habitual: Ajustes › Preferencias del dispositivo › Accesibilidad.")
                    .setPositiveButton("Abrir Accesibilidad",
                            (d, w) -> HomeGuardService.openAccessibilitySettings(this))
                    .setNegativeButton("Ahora no", null)
                    .show();
        }, 1200);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Ui.immersive(this);
    }

    /** Botones que en otros equipos "salen" del launcher: aquí solo vuelven arriba. */
    private static boolean isExitKey(int code) {
        return code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE
                || code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_DEL
                || code == KeyEvent.KEYCODE_EXPLORER;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isExitKey(event.getKeyCode())) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                long now = android.os.SystemClock.uptimeMillis();
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && prefs.lockDoubleBack()
                        && now - lastBackUp < 650) {
                    lastBackUp = 0;
                    requestLock();
                } else {
                    lastBackUp = event.getKeyCode() == KeyEvent.KEYCODE_BACK ? now : 0;
                    goTop();
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /** Botón Atrás o Inicio estando en el launcher: volver arriba. */
    @Override
    public void onBackPressed() {
        goTop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        goTop();
    }

    private void goTop() {
        if (scroll == null) return;
        scroll.smoothScrollTo(0, 0);
        lastFocusTag = null;
        restoreFocus();
    }

    // ------------------------------------------------------------------ Tema y fondo

    private void applyTheme() {
        t = LauncherTheme.get(prefs.theme());
        root.setBackground(Ui.background(t));
        int src = Wallpaper.resolve(this, prefs);
        File f = Wallpaper.file(this);
        String key = src == -2 ? "f" + f.lastModified() : "b" + src;
        Bitmap bm = null;
        if (src != -1 && !key.equals(wallKey)) {
            bm = src == -2 ? Wallpaper.load(this, 1920) : Wallpaper.loadBuiltin(this, src, 1920);
        }
        if (src == -1 || (bm == null && !key.equals(wallKey))) {
            wallImg.setVisibility(View.GONE);
            wallImg.setImageDrawable(null);
            wallKey = "";
            dimView.setBackgroundColor(Color.TRANSPARENT);
        } else {
            if (bm != null) {
                wallImg.setImageBitmap(bm);
                wallKey = key;
            }
            wallImg.setVisibility(View.VISIBLE);
            // En el tema claro se aclara el fondo; en los oscuros se oscurece
            int a = prefs.dim() * 255 / 100;
            boolean lightTheme = Color.red(t.text) < 128;
            dimView.setBackgroundColor(lightTheme ? Color.argb(a, 255, 255, 255) : Color.argb(a, 0, 0, 0));
        }
        buildClock();
        date.setTextColor(t.subtext);
        wIcon.setTextColor(t.text);
        wTemp.setTextColor(t.text);
        wDetail.setTextColor(t.subtext);
        recTitle.setTextColor(t.accent);
        cwTitle.setTextColor(t.accent);
        net.setTextColor(t.subtext);
        favTitle.setTextColor(t.accent);
        allTitle.setTextColor(t.accent);
        buildButtons();
    }

    private void buildButtons() {
        buttonBox.removeAllViews();
        btButton = addHeaderButton("Bluetooth", "btn:bt",
                () -> startActivity(new Intent(this, BluetoothActivity.class)));
        updateBtLabel();
        addHeaderButton("Ajustes", "btn:settings", () -> PinDialogs.require(this, prefs, null,
                () -> startActivity(new Intent(this, SettingsActivity.class))));
        addHeaderButton("Mantenimiento", "btn:maint", () -> PinDialogs.require(this, prefs, null,
                () -> startActivity(new Intent(this, MaintenanceActivity.class))));
        addHeaderButton("Android", "btn:android", () -> AppRepository.openSystemScreen(this,
                Settings.ACTION_SETTINGS));
    }

    /** Muestra cuántos dispositivos Bluetooth hay conectados (con un pequeño retraso para que Android actualice). */
    private void updateBtLabel() {
        if (btButton == null) return;
        handler.postDelayed(() -> {
            if (btButton == null) return;
            int n = BluetoothActivity.connectedCount(this);
            btButton.setText(n > 0 ? "Bluetooth · " + n : "Bluetooth");
        }, 700);
    }

    private TextView addHeaderButton(String label, String tag, Runnable action) {
        TextView b = Ui.button(this, t, label, action);
        b.setTag(tag);
        b.setTextSize(13);
        b.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        ((LinearLayout.LayoutParams) b.getLayoutParams()).leftMargin = Ui.dp(this, 8);
        final View.OnFocusChangeListener base = b.getOnFocusChangeListener();
        b.setOnFocusChangeListener((v, f) -> {
            base.onFocusChange(v, f);
            if (f) lastFocusTag = (String) v.getTag();
        });
        buttonBox.addView(b);
        return b;
    }

    // ------------------------------------------------------------------ Reloj y red

    /** Arma el reloj según el estilo elegido en Ajustes. */
    private void buildClock() {
        int st = prefs.clockStyle();
        if (st != builtClockStyle) {
            clockBox.removeAllViews();
            clock = null;
            ampm = null;
            analog = null;
            if (st == 3) {
                analog = new AnalogClockView(this);
                int s = Ui.dp(this, 62);
                clockBox.addView(analog, new LinearLayout.LayoutParams(s, s));
            } else {
                clock = Ui.text(this, "", 38, Color.WHITE, true);
                clockBox.addView(clock);
                if (st == 1) clock.setTextSize(32);
                if (st == 4) {
                    clock.setTextSize(46);
                    clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                }
                if (st == 5) {
                    clock.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                    clock.setTextSize(34);
                    clock.setLetterSpacing(0.12f);
                    clock.setPadding(Ui.dp(this, 12), Ui.dp(this, 2), Ui.dp(this, 12), Ui.dp(this, 2));
                }
                if (st == 2) {
                    ampm = Ui.text(this, "", 14, Color.WHITE, true);
                    ampm.setPadding(Ui.dp(this, 5), Ui.dp(this, 8), 0, 0);
                    clockBox.addView(ampm);
                }
            }
            builtClockStyle = st;
        }
        if (clock != null) {
            clock.setTextColor(st == 5 ? t.accent : t.text);
            clock.setBackground(st == 5 ? Ui.rounded(0x55000000, Ui.dp(this, 10), Ui.dp(this, 2),
                    (t.accent & 0x00FFFFFF) | 0x88000000) : null);
            if (st == 5) clock.setShadowLayer(Ui.dp(this, 8), 0, 0, t.accent);
            else clock.setShadowLayer(0, 0, 0, 0);
        }
        if (ampm != null) ampm.setTextColor(t.subtext);
        if (analog != null) analog.setColors(t.text, t.accent);
        updateClock();
    }

    private void updateClock() {
        if (clockBox == null || date == null) return;
        Date now = new Date();
        int st = prefs.clockStyle();
        if (clock != null) {
            String pattern = st == 1 ? "HH:mm:ss" : st == 2 ? "h:mm" : "HH:mm";
            clock.setText(new SimpleDateFormat(pattern, Locale.getDefault()).format(now));
        }
        if (ampm != null) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            ampm.setText(cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.AM ? "a. m." : "p. m.");
        }
        if (analog != null) analog.invalidate();
        String d = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "PY")).format(now);
        if (d.length() > 0) d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
        date.setText(d);
    }

    @SuppressWarnings("deprecation")
    private void updateNet() {
        if (net == null) return;
        String s;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) s = "Sin conexión a internet";
            else if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) s = "Conectado por cable";
            else if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                String ssid = wifiName();
                s = ssid != null ? "Conectado a " + ssid : "Conectado por Wi-Fi";
                if (ssid == null) askWifiNamePermission();
            } else s = "Conectado";
        } catch (Exception e) {
            s = "";
        }
        net.setText(s);
    }

    /** Nombre de la red Wi-Fi (Android 8.1+ pide permiso de ubicación para leerlo). */
    @SuppressWarnings("deprecation")
    private String wifiName() {
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return null;
            android.net.wifi.WifiInfo wi = wm.getConnectionInfo();
            if (wi == null) return null;
            String ssid = wi.getSSID();
            if (ssid == null) return null;
            if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() > 1) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }
            if (ssid.isEmpty() || ssid.contains("unknown ssid") || ssid.equals("0x")) return null;
            return ssid;
        } catch (Exception e) {
            return null;
        }
    }

    private static final int REQ_WIFI_NAME = 31;

    private void askWifiNamePermission() {
        if (Build.VERSION.SDK_INT < 23) return;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        if (prefs.wifiPermAsked()) return;
        prefs.setWifiPermAsked(true);
        handler.postDelayed(() -> {
            if (isFinishing()) return;
            try {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_WIFI_NAME);
            } catch (Exception ignored) {
            }
        }, 1500);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_WIFI_NAME) updateNet();
    }

    static String localIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> as = ni.getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress a = as.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------ Apps

    private void reload() {
        loading = true;
        if (apps.isEmpty()) render();
        final Context app = getApplicationContext();
        try {
            executor.execute(() -> {
                final List<AppItem> list = AppRepository.load(app, true);
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    loading = false;
                    needsReload = false;
                    apps.clear();
                    apps.addAll(list);
                    firstRunFavorites();
                    render();
                });
            });
        } catch (Exception ignored) {
        }
    }

    /** La primera vez, agrega como favoritas hasta 6 apps de TV. */
    private void firstRunFavorites() {
        if (prefs.favInit()) return;
        List<String> f = new ArrayList<>();
        for (AppItem a : apps) if (a.leanback && f.size() < 6) f.add(a.pkg);
        prefs.setFavorites(f);
        prefs.setFavInit(true);
    }

    private void render() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int cols = prefs.columns();
        int padH = Ui.dp(this, 48);
        int gap = Ui.dp(this, 20);
        int tileW = (dm.widthPixels - 2 * padH) / cols - gap;
        int tileH = tileW * 9 / 16;

        Set<String> hidden = prefs.hidden();
        Set<String> locked = prefs.locked();
        Map<String, AppItem> byPkg = new HashMap<>();
        for (AppItem a : apps) byPkg.put(a.pkg, a);

        // Favoritos
        favRow.removeAllViews();
        int favCount = 0;
        for (String k : prefs.favorites()) {
            AppItem a = byPkg.get(k);
            if (a == null || hidden.contains(k)) continue;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, -2);
            lp.setMargins(gap / 2, 0, gap / 2, 0);
            favRow.addView(makeTile(a, tileW, tileH, "fav:", locked.contains(k)), lp);
            favCount++;
        }
        if (favCount == 0) {
            TextView hint = Ui.text(this, loading ? "Cargando…"
                    : "Mantené presionado OK (o el botón Menú) sobre una app para agregarla a favoritos.",
                    15, t.subtext, false);
            hint.setPadding(gap / 2, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            favRow.addView(hint);
        }

        // Recientes
        recRow.removeAllViews();
        int recCount = 0;
        if (prefs.showRecents()) {
            for (String k : prefs.recents()) {
                AppItem a = byPkg.get(k);
                if (a == null || hidden.contains(k)) continue;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, -2);
                lp.setMargins(gap / 2, 0, gap / 2, 0);
                recRow.addView(makeTile(a, tileW, tileH, "rec:", locked.contains(k)), lp);
                recCount++;
            }
        }
        recTitle.setVisibility(recCount > 0 ? View.VISIBLE : View.GONE);
        recScroll.setVisibility(recCount > 0 ? View.VISIBLE : View.GONE);

        // Seguir viendo
        cwRow.removeAllViews();
        int cwCount = 0;
        if (prefs.showContinue()) {
            List<ContinueWatching.Entry> entries = ContinueWatching.list(this);
            for (int i = 0; i < entries.size(); i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, -2);
                lp.setMargins(gap / 2, 0, gap / 2, 0);
                cwRow.addView(makeContinueTile(entries.get(i), i, tileW, tileH), lp);
                cwCount++;
            }
        }
        cwTitle.setVisibility(cwCount > 0 ? View.VISIBLE : View.GONE);
        cwScroll.setVisibility(cwCount > 0 ? View.VISIBLE : View.GONE);

        // Todas las apps
        List<AppItem> visible = new ArrayList<>();
        for (AppItem a : apps) if (!hidden.contains(a.pkg)) visible.add(a);
        if ("usage".equals(prefs.sort())) {
            final JSONObject usage = prefs.usageJson();
            Collections.sort(visible, (x, y) -> {
                int d = usage.optInt(y.pkg, 0) - usage.optInt(x.pkg, 0);
                return d != 0 ? d : x.label.compareToIgnoreCase(y.label);
            });
        }
        grid.removeAllViews();
        grid.setColumnCount(cols);
        for (AppItem a : visible) {
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = tileW;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
            grid.addView(makeTile(a, tileW, tileH, "app:", locked.contains(a.pkg)), lp);
        }
        allTitle.setText(loading && apps.isEmpty() ? "CARGANDO APLICACIONES…"
                : "TODAS LAS APLICACIONES (" + visible.size() + ")");
        restoreFocus();
    }

    private void restoreFocus() {
        View target = null;
        if (lastFocusTag != null) {
            target = content.findViewWithTag(lastFocusTag);
            if (target == null && (lastFocusTag.startsWith("fav:") || lastFocusTag.startsWith("rec:"))) {
                target = content.findViewWithTag("app:" + lastFocusTag.substring(4));
            }
        }
        if (target == null && favRow.getChildCount() > 0 && favRow.getChildAt(0).isFocusable()) {
            target = favRow.getChildAt(0);
        }
        if (target == null && grid.getChildCount() > 0) target = grid.getChildAt(0);
        if (target == null && buttonBox.getChildCount() > 0) target = buttonBox.getChildAt(0);
        if (target != null) {
            final View v = target;
            v.post(v::requestFocus);
        }
    }

    private View makeTile(final AppItem a, int w, int h, String tagPrefix, boolean isLocked) {
        final boolean always = prefs.alwaysLabels();
        final LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setClipChildren(false);
        tile.setTag(tagPrefix + a.pkg);

        final float r = Ui.dp(this, 10);
        final FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.rounded(t.card, r, 0, 0));
        card.setClipToOutline(true);
        if (a.banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(a.banner);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (a.icon != null) iv.setImageDrawable(a.icon);
            else iv.setImageResource(android.R.drawable.sym_def_app_icon);
            final float[] sizes = {0.40f, 0.58f, 0.74f, 0.90f};
            int si = Math.max(0, Math.min(3, prefs.iconSize()));
            int s = Math.round(h * sizes[si]);
            card.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }
        if (isLocked) {
            TextView lock = Ui.text(this, "🔒", 14, Color.WHITE, false);
            lock.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2));
            lock.setBackground(Ui.rounded(0x99000000, Ui.dp(this, 8), 0, 0));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            lp.setMargins(0, Ui.dp(this, 6), Ui.dp(this, 6), 0);
            card.addView(lock, lp);
        }
        tile.addView(card, new LinearLayout.LayoutParams(w, h));

        final TextView label = Ui.text(this, a.label, 14, t.subtext, false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setPadding(0, Ui.dp(this, 6), 0, 0);
        label.setVisibility(always ? View.VISIBLE : View.INVISIBLE);
        tile.addView(label, new LinearLayout.LayoutParams(w, -2));

        tile.setOnFocusChangeListener((v, f) -> {
            Ui.focusAnim(tile, f, 1.08f);
            card.setForeground(f ? Ui.rounded(Color.TRANSPARENT, r, Ui.dp(this, 3), t.accent) : null);
            label.setTextColor(f ? t.text : t.subtext);
            label.setTypeface(f ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            if (!always) label.setVisibility(f ? View.VISIBLE : View.INVISIBLE);
            if (f) lastFocusTag = (String) tile.getTag();
        });
        tile.setOnClickListener(v -> open(a));
        tile.setOnLongClickListener(v -> {
            showOptions(a);
            return true;
        });
        tile.setOnKeyListener((v, code, ev) -> {
            if (code == KeyEvent.KEYCODE_MENU && ev.getAction() == KeyEvent.ACTION_UP) {
                showOptions(a);
                return true;
            }
            return false;
        });
        return tile;
    }

    private void open(AppItem a) {
        Runnable go = () -> {
            prefs.addUsage(a.pkg);
            prefs.addRecent(a.pkg);
            if (!AppRepository.launch(this, a)) Ui.toast(this, "No se pudo abrir " + a.label);
        };
        if (prefs.locked().contains(a.pkg)) {
            PinDialogs.require(this, prefs, "PIN para abrir " + a.label, go);
        } else {
            go.run();
        }
    }

    // ------------------------------------------------------------------ Menú de cada app

    private void showOptions(final AppItem a) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final List<String> favs = prefs.favorites();
        final boolean isFav = favs.contains(a.pkg);
        final boolean isLocked = prefs.locked().contains(a.pkg);

        labels.add("Abrir");
        actions.add(() -> open(a));
        if (!isFav) {
            labels.add("Agregar a favoritos");
            actions.add(() -> {
                favs.add(a.pkg);
                prefs.setFavorites(favs);
                lastFocusTag = "fav:" + a.pkg;
                render();
            });
        } else {
            labels.add("Mover a la izquierda");
            actions.add(() -> moveFavorite(a.pkg, -1));
            labels.add("Mover a la derecha");
            actions.add(() -> moveFavorite(a.pkg, 1));
            labels.add("Quitar de favoritos");
            actions.add(() -> {
                favs.remove(a.pkg);
                prefs.setFavorites(favs);
                lastFocusTag = "app:" + a.pkg;
                render();
            });
        }
        labels.add("Ocultar aplicación");
        actions.add(() -> PinDialogs.require(this, prefs, null, () -> {
            Set<String> h = prefs.hidden();
            h.add(a.pkg);
            prefs.setHidden(h);
            lastFocusTag = null;
            render();
            Ui.toast(this, "Oculta. Podés volver a mostrarla en Ajustes › Apps ocultas");
        }));
        if (isLocked) {
            labels.add("Quitar bloqueo con PIN");
            actions.add(() -> PinDialogs.require(this, prefs, null, () -> setLocked(a.pkg, false)));
        } else {
            labels.add("Bloquear con PIN");
            actions.add(() -> {
                if (!prefs.hasPin()) {
                    Ui.toast(this, "Primero creá un PIN");
                    PinDialogs.create(this, prefs, () -> setLocked(a.pkg, true));
                } else {
                    PinDialogs.require(this, prefs, null, () -> setLocked(a.pkg, true));
                }
            });
        }
        labels.add("Información de la app");
        actions.add(() -> AppRepository.openAppInfo(this, a.pkg));
        if (!a.system) {
            labels.add("Desinstalar");
            actions.add(() -> PinDialogs.require(this, prefs, null,
                    () -> AppRepository.uninstall(this, a.pkg)));
        }

        new AlertDialog.Builder(this)
                .setTitle(a.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void setLocked(String pkg, boolean lock) {
        Set<String> l = prefs.locked();
        if (lock) l.add(pkg);
        else l.remove(pkg);
        prefs.setLocked(l);
        render();
        Ui.toast(this, lock ? "App bloqueada: pedirá el PIN al abrirla" : "Bloqueo quitado");
    }

    private void moveFavorite(String pkg, int dir) {
        List<String> f = prefs.favorites();
        int i = f.indexOf(pkg);
        int j = i + dir;
        if (i < 0 || j < 0 || j >= f.size()) return;
        Collections.swap(f, i, j);
        prefs.setFavorites(f);
        lastFocusTag = "fav:" + pkg;
        render();
    }
}
