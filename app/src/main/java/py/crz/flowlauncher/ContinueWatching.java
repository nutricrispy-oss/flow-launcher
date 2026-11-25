package py.crz.flowlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fila «Seguir viendo»: accesos directos a videos, series, listas y música.
 * Se cargan desde el archivo «seguir-viendo.txt» del repositorio de GitHub,
 * una línea por acceso:   Nombre | link | (imagen opcional)
 */
final class ContinueWatching {
    static final String FILE = "seguir-viendo.txt";

    private ContinueWatching() {}

    static final class Entry {
        String name, link, image;
    }

    static final class Result {
        int total;
        String error;
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "seguir");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File listFile(Context c) {
        return new File(dir(c), "lista.json");
    }

    /** Miniatura descargada para un acceso (puede no existir). */
    static File thumb(Context c, Entry e) {
        return new File(dir(c), "t" + Integer.toHexString((e.link + "|" + e.image).hashCode()) + ".jpg");
    }

    /** Accesos guardados en el equipo (funciona sin internet). */
    static List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<>();
        try (InputStream in = new FileInputStream(listFile(c))) {
            JSONArray a = new JSONArray(Backup.readAll(in));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.name = o.optString("name");
                e.link = o.optString("link");
                e.image = o.optString("image", "");
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Lee el archivo de GitHub, guarda la lista y descarga las miniaturas. Fuera del hilo principal. */
    static Result sync(Context c, String repo, String file) {
        Result r = new Result();
        try {
            String api = "https://api.github.com/repos/" + repo.trim() + "/contents/" + file;
            JSONObject o = new JSONObject(get(api));
            String text = new String(Base64.decode(o.optString("content", ""), Base64.DEFAULT), "UTF-8");
            List<Entry> entries = parse(text);
            JSONArray arr = new JSONArray();
            Set<String> keepThumbs = new HashSet<>();
            for (Entry e : entries) {
                arr.put(new JSONObject().put("name", e.name).put("link", e.link).put("image", e.image));
                File t = thumb(c, e);
                keepThumbs.add(t.getName());
                if (t.exists()) continue;
                String img = imageUrl(e);
                if (img == null) continue;
                try {
                    File raw = new File(c.getCacheDir(), "miniatura.tmp");
                    download(img, raw);
                    Wallpaper.compressTo(c, Uri.fromFile(raw), t);
                    raw.delete();
                } catch (Exception ignored) {
                }
            }
            try (OutputStream out = new FileOutputStream(listFile(c))) {
                out.write(arr.toString().getBytes("UTF-8"));
            }
            File[] files = dir(c).listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().startsWith("t") && !keepThumbs.contains(f.getName())) f.delete();
                }
            }
            r.total = entries.size();
        } catch (FileNotFoundException e) {
            r.error = "No existe el archivo «" + file + "» en " + repo;
        } catch (Exception e) {
            r.error = "No se pudo conectar (" + e.getMessage() + ")";
        }
        return r;
    }

    static List<Entry> parse(String text) {
        List<Entry> out = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\|");
            Entry e = new Entry();
            if (p.length == 1) {
                e.link = p[0].trim();
                e.name = "";
            } else {
                e.name = p[0].trim();
                e.link = p[1].trim();
                e.image = p.length > 2 ? p[2].trim() : "";
            }
            if (e.image == null) e.image = "";
            if (!e.link.contains(":")) continue;
            if (e.name.isEmpty()) e.name = guessName(e.link);
            out.add(e);
        }
        return out;
    }

    private static String guessName(String link) {
        if (youtubeId(link) != null) return "Video de YouTube";
        if (link.contains("spotify")) return "Spotify";
        if (link.contains("netflix")) return "Netflix";
        return link;
    }

    /** ID de un video de YouTube a partir del link, o null. */
    static String youtubeId(String link) {
        Matcher m = Pattern.compile("(?:youtu\\.be/|v=|/shorts/|/embed/|/live/)([A-Za-z0-9_-]{11})").matcher(link);
        return m.find() ? m.group(1) : null;
    }

    private static String imageUrl(Entry e) {
        if (e.image != null && e.image.startsWith("http")) return e.image;
        String id = youtubeId(e.link);
        if (id != null) return "https://img.youtube.com/vi/" + id + "/hqdefault.jpg";
        return null;
    }

    // ---- Abrir ----

    /** Apps de TV preferidas para cada servicio (si están instaladas). */
    private static String preferredPackage(Context c, String link) {
        String l = link.toLowerCase();
        String[] candidates;
        if (l.contains("youtube.com") || l.contains("youtu.be")) {
            candidates = new String[]{"com.google.android.youtube.tv", "com.google.android.youtube.googletv",
                    "com.google.android.youtube"};
        } else if (l.contains("spotify")) {
            candidates = new String[]{"com.spotify.tv.android", "com.spotify.music"};
        } else if (l.contains("netflix.com")) {
            candidates = new String[]{"com.netflix.ninja", "com.netflix.mediaclient"};
        } else {
            return null;
        }
        PackageManager pm = c.getPackageManager();
        for (String pkg : candidates) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static boolean open(Context c, Entry e) {
        Uri uri = Uri.parse(e.link);
        String pkg = preferredPackage(c, e.link);
        if (pkg != null) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                i.setPackage(pkg);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            } catch (Exception ignored) {
            }
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Ícono de la app que abre el link (para la insignia de la tarjeta). */
    static Drawable appIcon(Context c, Entry e) {
        try {
            PackageManager pm = c.getPackageManager();
            String pkg = preferredPackage(c, e.link);
            if (pkg != null) return pm.getApplicationIcon(pkg);
            ResolveInfo ri = pm.resolveActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(e.link)), 0);
            if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)) {
                return ri.loadIcon(pm);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ---- Red ----

    private static String get(String url) throws Exception {
        HttpURLConnection con = open(url);
        try (InputStream in = con.getInputStream()) {
            return Backup.readAll(in);
        } finally {
            con.disconnect();
        }
    }

    private static void download(String url, File dst) throws Exception {
        HttpURLConnection con = open(url);
        try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            con.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "FlowLauncher");
        con.setRequestProperty("Accept", "application/vnd.github+json");
        int code = con.getResponseCode();
        if (code == 404) throw new FileNotFoundException(url);
        if (code >= 300) throw new Exception("error " + code);
        return con;
    }
}
