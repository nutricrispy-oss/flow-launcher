package py.crz.flowlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Busca versiones nuevas en GitHub Releases y las instala. */
final class Updater {
    static final String REPO = "nutricrispy-oss/flow-launcher";
    private static final long CHECK_EVERY = 6L * 60 * 60 * 1000; // cada 6 horas

    private Updater() {}

    static final class Release {
        int versionCode;
        String versionName, apkUrl;
    }

    static int currentVersionCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static String currentVersionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    static boolean shouldAutoCheck(Context c, Prefs p) {
        return p.autoUpdate() && System.currentTimeMillis() - p.lastUpdateCheck() > CHECK_EVERY;
    }

    /** Devuelve la última versión publicada, o null si hay error. Fuera del hilo principal. */
    static Release latest() {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(
                    "https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
            con.setConnectTimeout(12000);
            con.setReadTimeout(15000);
            con.setRequestProperty("User-Agent", "FlowLauncher");
            con.setRequestProperty("Accept", "application/vnd.github+json");
            String body;
            try (InputStream in = con.getInputStream()) {
                body = Backup.readAll(in);
            } finally {
                con.disconnect();
            }
            JSONObject o = new JSONObject(body);
            Release r = new Release();
            String notes = o.optString("body", "");
            Matcher m = Pattern.compile("versionCode[:=]\\s*(\\d+)").matcher(notes);
            if (!m.find()) return null;
            r.versionCode = Integer.parseInt(m.group(1));
            Matcher n = Pattern.compile("versionName[:=]\\s*([\\w.\\-]+)").matcher(notes);
            r.versionName = n.find() ? n.group(1) : String.valueOf(r.versionCode);
            JSONArray assets = o.optJSONArray("assets");
            if (assets == null) return null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").endsWith(".apk")) r.apkUrl = a.optString("browser_download_url");
            }
            return r.apkUrl == null ? null : r;
        } catch (Exception e) {
            return null;
        }
    }

    /** Pregunta y, si el usuario acepta, descarga e instala. */
    static void offer(Activity a, Release r) {
        new AlertDialog.Builder(a)
                .setTitle("Hay una versión nueva de Flow Launcher")
                .setMessage("Versión instalada: " + currentVersionName(a) + "\nVersión nueva: " + r.versionName
                        + "\n\nSe descarga e instala encima, sin perder tu configuración.")
                .setPositiveButton("Actualizar ahora", (d, w) -> downloadAndInstall(a, r))
                .setNegativeButton("Más tarde", null)
                .show();
    }

    static void downloadAndInstall(Activity a, Release r) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle("Falta un permiso")
                    .setMessage("Para actualizarse sola, Flow Launcher necesita permiso para instalar apps.\n\n"
                            + "En la pantalla que se abre, activá «Permitir de esta fuente» y volvé a intentarlo.")
                    .setPositiveButton("Abrir permiso", (d, w) -> {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + a.getPackageName())));
                        } catch (Exception e) {
                            AppRepository.openSystemScreen(a, Settings.ACTION_SECURITY_SETTINGS);
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        Ui.toast(a, "Descargando la actualización…");
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            try {
                File apk = ApkProvider.file(app);
                HttpURLConnection con = (HttpURLConnection) new URL(r.apkUrl).openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(60000);
                con.setInstanceFollowRedirects(true);
                con.setRequestProperty("User-Agent", "FlowLauncher");
                int code = con.getResponseCode();
                if (code >= 300) throw new Exception("GitHub respondió " + code);
                try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    con.disconnect();
                }
                if (apk.length() < 10000) throw new Exception("el archivo descargado está incompleto");
                a.runOnUiThread(() -> openInstaller(a, apk));
            } catch (Exception e) {
                a.runOnUiThread(() -> showError(a, "No se pudo descargar la actualización", e));
            }
        }).start();
    }

    /** Abre el instalador de Android, igual que hace Downloader. Si no se puede, usa el método interno. */
    private static void openInstaller(Activity a, File apk) {
        Exception first = null;
        try {
            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = ApkProvider.uri();
            } else {
                // Android 6 o anterior: el instalador necesita un archivo legible
                File ext = new File(a.getExternalFilesDir(null), "FlowLauncher.apk");
                copy(apk, ext);
                uri = Uri.fromFile(ext);
            }
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, ApkProvider.MIME);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
            return;
        } catch (Exception e) {
            first = e;
        }
        try {
            install(a.getApplicationContext(), apk);
        } catch (Exception e) {
            showError(a, "No se pudo abrir el instalador", first != null ? first : e);
        }
    }

    static void showError(Activity a, String title, Exception e) {
        if (a.isFinishing()) return;
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage("Detalle del error (sacale una foto y mandásela a Claude):\n\n"
                        + e.getClass().getSimpleName() + ": " + e.getMessage()
                        + "\n\nMientras tanto podés actualizar con Downloader usando el link de siempre.")
                .setPositiveButton("Entendido", null)
                .show();
    }

    private static void copy(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = s.openWrite("flowlauncher", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent i = new Intent(c, UpdateReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= 0x02000000; // FLAG_MUTABLE
            PendingIntent sender = PendingIntent.getBroadcast(c, 55, i, flags);
            s.commit(sender.getIntentSender());
        }
    }
}
