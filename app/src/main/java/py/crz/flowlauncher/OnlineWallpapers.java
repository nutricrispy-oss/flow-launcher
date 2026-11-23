package py.crz.flowlauncher;

import android.content.Context;
import android.net.Uri;

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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fondos desde una carpeta del repositorio de GitHub.
 * Subís imágenes a la carpeta «fondos» del repo y el launcher las descarga.
 */
final class OnlineWallpapers {
    static final String DEFAULT_REPO = "nutricrispy-oss/flow-launcher";
    static final String FOLDER = "fondos";
    private static final long MAX_BYTES = 25L * 1024 * 1024;

    private OnlineWallpapers() {}

    static final class Result {
        int total, downloaded, removed;
        String error;
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), FOLDER);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Imágenes ya descargadas, ordenadas por nombre. */
    static List<File> list(Context c) {
        List<File> out = new ArrayList<>();
        File[] files = dir(c).listFiles();
        if (files == null) return out;
        Arrays.sort(files);
        for (File f : files) if (f.getName().endsWith(".jpg")) out.add(f);
        return out;
    }

    /** Nombre bonito para mostrar: «sub-zero_hielo.jpg» → «sub-zero hielo». */
    static String displayName(File f) {
        String n = f.getName();
        if (n.endsWith(".jpg")) n = n.substring(0, n.length() - 4);
        return n.replace('_', ' ');
    }

    private static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    private static String localName(String name) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base.replaceAll("[^A-Za-z0-9áéíóúÁÉÍÓÚñÑ _.-]", "_") + ".jpg";
    }

    /** Descarga las nuevas o cambiadas y borra las que ya no están en GitHub. */
    static Result sync(Context c, String repo) {
        Result r = new Result();
        File dir = dir(c);
        try {
            String api = "https://api.github.com/repos/" + repo.trim() + "/contents/" + FOLDER;
            JSONArray arr = new JSONArray(httpGetText(api));
            Set<String> keep = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String name = o.optString("name", "");
                if (!"file".equals(o.optString("type")) || !isImage(name)) continue;
                if (o.optLong("size", 0) > MAX_BYTES) continue;
                String url = o.optString("download_url", "");
                String sha = o.optString("sha", "");
                if (url.isEmpty()) continue;
                String local = localName(name);
                keep.add(local);
                File f = new File(dir, local);
                File shaFile = new File(dir, local + ".sha");
                if (f.exists() && sha.equals(readText(shaFile))) continue;
                File raw = new File(c.getCacheDir(), "descarga.tmp");
                download(url, raw);
                boolean ok = Wallpaper.compressTo(c, Uri.fromFile(raw), f);
                raw.delete();
                if (ok) {
                    writeText(shaFile, sha);
                    r.downloaded++;
                }
            }
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String n = f.getName();
                    String base = n.endsWith(".sha") ? n.substring(0, n.length() - 4) : n;
                    if (!keep.contains(base)) {
                        if (n.endsWith(".jpg")) r.removed++;
                        f.delete();
                    }
                }
            }
            r.total = keep.size();
        } catch (FileNotFoundException e) {
            r.error = "No se encontró la carpeta «" + FOLDER + "» en " + repo;
        } catch (Exception e) {
            r.error = "No se pudo conectar (" + e.getMessage() + ")";
        }
        return r;
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
        if (code == 403 || code == 429) throw new Exception("GitHub pide esperar unos minutos");
        if (code >= 300) throw new Exception("error " + code);
        return con;
    }

    private static String httpGetText(String url) throws Exception {
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

    private static String readText(File f) {
        if (!f.exists()) return "";
        try (InputStream in = new FileInputStream(f)) {
            return Backup.readAll(in).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeText(File f, String s) {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }
}
