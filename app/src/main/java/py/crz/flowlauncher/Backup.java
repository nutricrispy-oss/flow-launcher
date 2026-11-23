package py.crz.flowlauncher;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Copias de seguridad de toda la configuración (incluye el fondo de pantalla). */
final class Backup {
    private Backup() {}

    static String build(Context c, Prefs p) throws Exception {
        JSONObject o = p.toJson();
        o.put("app", "FlowLauncher");
        o.put("formato", 1);
        o.put("fecha", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));
        if (p.wallpaperImage()) {
            byte[] w = Wallpaper.readBytes(c);
            if (w != null) o.put("wallpaper", Base64.encodeToString(w, Base64.NO_WRAP));
        }
        return o.toString(2);
    }

    static void restore(Context c, Prefs p, String json) throws Exception {
        JSONObject o = new JSONObject(json);
        if (!"FlowLauncher".equals(o.optString("app"))) {
            throw new Exception("El archivo no es una copia de Flow Launcher");
        }
        p.fromJson(o);
        String w = o.optString("wallpaper", "");
        if (!w.isEmpty()) Wallpaper.saveBytes(c, Base64.decode(w, Base64.DEFAULT));
        else p.setWallpaperImage(false);
        HomeActivity.needsReload = true;
    }

    static String fileName() {
        return "flowlauncher-copia-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".json";
    }

    // ---- Copia interna rápida ----
    static File internalFile(Context c) {
        File d = c.getExternalFilesDir(null);
        if (d == null) d = c.getFilesDir();
        return new File(d, "flowlauncher-copia.json");
    }

    static void saveInternal(Context c, Prefs p) throws Exception {
        try (OutputStream out = new FileOutputStream(internalFile(c))) {
            out.write(build(c, p).getBytes("UTF-8"));
        }
    }

    static void restoreInternal(Context c, Prefs p) throws Exception {
        try (InputStream in = new FileInputStream(internalFile(c))) {
            restore(c, p, readAll(in));
        }
    }

    /** Guarda la copia en la carpeta Descargas. Devuelve dónde quedó. */
    static String saveToDownloads(Context c, Prefs p) throws Exception {
        String json = build(c, p);
        String name = fileName();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver r = c.getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new Exception("No se pudo crear el archivo");
            try (OutputStream out = r.openOutputStream(uri)) {
                if (out == null) throw new Exception("No se pudo escribir el archivo");
                out.write(json.getBytes("UTF-8"));
            }
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("No existe la carpeta Descargas");
            try (OutputStream out = new FileOutputStream(new File(dir, name))) {
                out.write(json.getBytes("UTF-8"));
            }
        }
        return "Descargas/" + name;
    }

    static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }
}
