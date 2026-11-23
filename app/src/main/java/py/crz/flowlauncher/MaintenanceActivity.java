package py.crz.flowlauncher;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Centro de mantenimiento: estado del equipo y herramientas de limpieza. */
public class MaintenanceActivity extends BaseActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView ramValue, ramDetail, stoValue, stoDetail, devDetail;
    private ProgressBar ramBar, stoBar;

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refreshStats();
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        render(focusedRow());
        handler.removeCallbacks(refresher);
        handler.post(refresher);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresher);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void render(int focus) {
        clear();
        header("Centro de mantenimiento", "Estado del equipo y herramientas de limpieza");

        // Tarjetas de estado (RAM · Almacenamiento · Equipo)
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        LinearLayout ram = card("MEMORIA RAM");
        ramValue = Ui.text(this, "", 24, t.text, true);
        ramBar = bar();
        ramDetail = Ui.text(this, "", 13, t.subtext, false);
        ram.addView(ramValue);
        ram.addView(ramBar);
        ram.addView(ramDetail);
        LinearLayout sto = card("ALMACENAMIENTO INTERNO");
        stoValue = Ui.text(this, "", 24, t.text, true);
        stoBar = bar();
        stoDetail = Ui.text(this, "", 13, t.subtext, false);
        sto.addView(stoValue);
        sto.addView(stoBar);
        sto.addView(stoDetail);
        LinearLayout dev = card("EQUIPO");
        devDetail = Ui.text(this, "", 13, t.subtext, false);
        devDetail.setLineSpacing(0, 1.2f);
        dev.addView(devDetail);
        cards.addView(ram, weight(true));
        cards.addView(sto, weight(true));
        cards.addView(dev, weight(false));
        body.addView(cards);

        section("Limpieza y rendimiento");
        row("Liberar memoria RAM", "Cierra las apps que quedaron abiertas en segundo plano", this::freeRam);
        row("Limpiar caché del launcher", "Borra archivos temporales de Flow Launcher", this::clearOwnCache);
        row("Recargar lista de aplicaciones", "Útil si una app nueva no aparece", () -> {
            HomeActivity.needsReload = true;
            Ui.toast(this, "La lista se recargará al volver al inicio");
        });
        row("Reiniciar Flow Launcher", "Cierra y vuelve a abrir el launcher", this::restartLauncher);

        section("Aplicaciones y sistema");
        row("Administrador de aplicaciones", "Abrir, ver información o desinstalar apps",
                () -> startActivity(new Intent(this, AppManagerActivity.class)));
        row("Apps de Android (borrar caché / forzar detención)", "Pantalla de aplicaciones del sistema",
                () -> AppRepository.openSystemScreen(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
                        Settings.ACTION_APPLICATION_SETTINGS));
        row("Almacenamiento del sistema", "Ver qué ocupa espacio",
                () -> AppRepository.openSystemScreen(this, Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                        Settings.ACTION_SETTINGS));
        row("Configuración de red", "Wi-Fi o cable de red",
                () -> AppRepository.openSystemScreen(this, Settings.ACTION_WIFI_SETTINGS,
                        Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS));
        row("Fecha y hora", "Corregir la hora si el reloj aparece mal",
                () -> AppRepository.openSystemScreen(this, Settings.ACTION_DATE_SETTINGS,
                        Settings.ACTION_SETTINGS));

        refreshStats();
        focusRow(focus);
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        c.setPadding(p, p, p, p);
        c.setBackground(Ui.rounded(t.card, Ui.dp(this, 14), 0, 0));
        TextView tt = Ui.text(this, title, 12, t.accent, true);
        tt.setLetterSpacing(0.08f);
        c.addView(tt);
        return c;
    }

    private LinearLayout.LayoutParams weight(boolean gapRight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        if (gapRight) lp.rightMargin = Ui.dp(this, 14);
        return lp;
    }

    private ProgressBar bar() {
        ProgressBar b = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        b.setMax(100);
        b.setProgressTintList(ColorStateList.valueOf(t.accent));
        b.setProgressBackgroundTintList(ColorStateList.valueOf(t.subtext));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 10));
        lp.topMargin = Ui.dp(this, 6);
        lp.bottomMargin = Ui.dp(this, 6);
        b.setLayoutParams(lp);
        return b;
    }

    private long[] ram() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return new long[]{mi.totalMem, mi.availMem};
    }

    private void refreshStats() {
        if (ramValue == null) return;
        try {
            long[] r = ram();
            long used = r[0] - r[1];
            int pct = r[0] > 0 ? (int) (used * 100 / r[0]) : 0;
            ramValue.setText(pct + "% en uso");
            ramBar.setProgress(pct);
            ramDetail.setText(Ui.formatBytes(used) + " usados de " + Ui.formatBytes(r[0])
                    + "  ·  libres " + Ui.formatBytes(r[1]));
        } catch (Exception ignored) {
        }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
            long used = total - free;
            int pct = total > 0 ? (int) (used * 100 / total) : 0;
            stoValue.setText(pct + "% ocupado");
            stoBar.setProgress(pct);
            stoDetail.setText(Ui.formatBytes(free) + " libres de " + Ui.formatBytes(total));
        } catch (Exception ignored) {
        }
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        long up = SystemClock.elapsedRealtime() / 1000;
        String ip = HomeActivity.localIp();
        devDetail.setText(Build.MANUFACTURER + " " + Build.MODEL
                + "\nAndroid " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nPantalla " + dm.widthPixels + " × " + dm.heightPixels
                + "\nIP " + (ip == null ? "sin conexión" : ip)
                + "\nEncendido hace " + (up / 3600) + " h " + ((up % 3600) / 60) + " min");
    }

    // ------------------------------------------------------------------ Acciones

    private void freeRam() {
        Ui.toast(this, "Liberando memoria…");
        executor.execute(() -> {
            final Context c = getApplicationContext();
            long before = ram()[1];
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            int count = 0;
            try {
                List<ApplicationInfo> list = c.getPackageManager().getInstalledApplications(0);
                for (ApplicationInfo ai : list) {
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                    if (ai.packageName.equals(c.getPackageName())) continue;
                    try {
                        am.killBackgroundProcesses(ai.packageName);
                        count++;
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
            SystemClock.sleep(1500);
            long freed = Math.max(0, ram()[1] - before);
            final String msg = "Se revisaron " + count + " apps.\nMemoria liberada: " + Ui.formatBytes(freed)
                    + "\nMemoria libre ahora: " + Ui.formatBytes(ram()[1]);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                refreshStats();
                new AlertDialog.Builder(this)
                        .setTitle("Memoria liberada")
                        .setMessage(msg)
                        .setPositiveButton("Listo", null)
                        .show();
            });
        });
    }

    private void clearOwnCache() {
        long size = delete(getCacheDir()) + delete(getExternalCacheDir());
        Ui.toast(this, "Caché limpiada (" + Ui.formatBytes(size) + ")");
    }

    /** Borra el contenido de una carpeta y devuelve cuántos bytes liberó. */
    private static long delete(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long total = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                total += delete(f);
                f.delete();
            } else {
                total += f.length();
                f.delete();
            }
        }
        return total;
    }

    private void restartLauncher() {
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        int flags = PendingIntent.FLAG_CANCEL_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= 0x04000000; // FLAG_IMMUTABLE
        PendingIntent pi = PendingIntent.getActivity(this, 77, i, flags);
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.set(AlarmManager.RTC, System.currentTimeMillis() + 600, pi);
        finishAffinity();
        handler.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 200);
    }
}
