package py.crz.flowlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Toda la configuración del launcher (guardada en el equipo). */
final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("flow_launcher", Context.MODE_PRIVATE);
    }

    // ---- Apariencia ----
    int theme() { return sp.getInt("theme", 0); }
    void setTheme(int v) { sp.edit().putInt("theme", v).apply(); }

    int columns() { return sp.getInt("columns", 5); }
    void setColumns(int v) { sp.edit().putInt("columns", v).apply(); }

    boolean alwaysLabels() { return sp.getBoolean("labels", true); }
    void setAlwaysLabels(boolean v) { sp.edit().putBoolean("labels", v).apply(); }

    /** "az" = alfabético, "usage" = más usadas primero */
    String sort() { return sp.getString("sort", "az"); }
    void setSort(String v) { sp.edit().putString("sort", v).apply(); }

    int dim() { return sp.getInt("dim", 30); }
    void setDim(int v) { sp.edit().putInt("dim", v).apply(); }

    static final String WP_AUTO = "auto", WP_GRADIENT = "gradient", WP_BUILTIN = "builtin", WP_CUSTOM = "custom";

    /** Fondo: auto = imagen del tema, gradient = solo colores, builtin = imagen incluida, custom = imagen propia */
    String wallpaperMode() {
        String m = sp.getString("wp_mode", null);
        if (m == null) m = sp.getBoolean("wall_img", false) ? WP_CUSTOM : WP_AUTO;
        return m;
    }
    void setWallpaperMode(String m) { sp.edit().putString("wp_mode", m).apply(); }

    int wallpaperBuiltin() { return sp.getInt("wp_builtin", 0); }
    void setWallpaperBuiltin(int i) { sp.edit().putString("wp_mode", WP_BUILTIN).putInt("wp_builtin", i).apply(); }

    boolean bootStart() { return sp.getBoolean("boot", false); }
    void setBootStart(boolean v) { sp.edit().putBoolean("boot", v).apply(); }

    /** Volver siempre a Flow Launcher cuando aparece el inicio de fábrica. */
    boolean guard() { return sp.getBoolean("guard", true); }
    void setGuard(boolean v) { sp.edit().putBoolean("guard", v).apply(); }

    boolean homeHintShown() { return sp.getBoolean("home_hint", false); }
    void setHomeHintShown(boolean v) { sp.edit().putBoolean("home_hint", v).apply(); }

    /** Repositorio de GitHub (usuario/repo) donde está la carpeta «fondos». */
    String fondosRepo() { return sp.getString("fondos_repo", OnlineWallpapers.DEFAULT_REPO); }
    void setFondosRepo(String v) { sp.edit().putString("fondos_repo", v).apply(); }

    // ---- v1.4 ----
    boolean autoUpdate() { return sp.getBoolean("auto_update", true); }
    void setAutoUpdate(boolean v) { sp.edit().putBoolean("auto_update", v).apply(); }
    long lastUpdateCheck() { return sp.getLong("upd_check", 0); }
    void setLastUpdateCheck(long v) { sp.edit().putLong("upd_check", v).apply(); }

    boolean autoClean() { return sp.getBoolean("auto_clean", false); }
    void setAutoClean(boolean v) { sp.edit().putBoolean("auto_clean", v).apply(); }

    /** 0 digital, 1 con segundos, 2 de 12 horas, 3 analógico, 4 minimalista, 5 retro LED */
    int clockStyle() { return sp.getInt("clock_style", 0); }
    void setClockStyle(int v) { sp.edit().putInt("clock_style", v).apply(); }

    /** 0 pequeño, 1 normal, 2 grande, 3 muy grande */
    int iconSize() { return sp.getInt("icon_size", 1); }
    void setIconSize(int v) { sp.edit().putInt("icon_size", v).apply(); }

    boolean showWeather() { return sp.getBoolean("weather", true); }
    void setShowWeather(boolean v) { sp.edit().putBoolean("weather", v).apply(); }

    boolean showRecents() { return sp.getBoolean("recents_on", true); }
    void setShowRecents(boolean v) { sp.edit().putBoolean("recents_on", v).apply(); }

    List<String> recents() { return list("recents"); }

    void addRecent(String pkg) {
        List<String> r = recents();
        r.remove(pkg);
        r.add(0, pkg);
        while (r.size() > 12) r.remove(r.size() - 1);
        putList("recents", r);
    }

    void clearRecents() { putList("recents", new ArrayList<String>()); }

    boolean favInit() { return sp.getBoolean("fav_init", false); }
    void setFavInit(boolean v) { sp.edit().putBoolean("fav_init", v).apply(); }

    // ---- Listas de apps ----
    List<String> favorites() { return list("favorites"); }
    void setFavorites(Collection<String> v) { putList("favorites", v); }

    Set<String> hidden() { return new HashSet<>(list("hidden")); }
    void setHidden(Collection<String> v) { putList("hidden", v); }

    Set<String> locked() { return new HashSet<>(list("locked")); }
    void setLocked(Collection<String> v) { putList("locked", v); }

    // ---- PIN ----
    boolean hasPin() { return sp.getString("pin", "").length() > 0; }

    void setPin(String pin) {
        sp.edit().putString("pin", pin == null || pin.isEmpty() ? "" : hash(pin)).apply();
    }

    boolean checkPin(String pin) {
        return hasPin() && hash(pin).equals(sp.getString("pin", ""));
    }

    // ---- Uso de apps ----
    JSONObject usageJson() {
        try {
            return new JSONObject(sp.getString("usage", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    void addUsage(String pkg) {
        JSONObject u = usageJson();
        try {
            u.put(pkg, u.optInt(pkg, 0) + 1);
        } catch (JSONException ignored) {
        }
        sp.edit().putString("usage", u.toString()).apply();
    }

    // ---- Copia de seguridad ----
    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("theme", theme());
        o.put("columns", columns());
        o.put("labels", alwaysLabels());
        o.put("sort", sort());
        o.put("dim", dim());
        o.put("wp_mode", wallpaperMode());
        o.put("wp_builtin", wallpaperBuiltin());
        o.put("boot", bootStart());
        o.put("guard", guard());
        o.put("fondos_repo", fondosRepo());
        o.put("auto_update", autoUpdate());
        o.put("auto_clean", autoClean());
        o.put("clock_style", clockStyle());
        o.put("icon_size", iconSize());
        o.put("weather", showWeather());
        o.put("recents_on", showRecents());
        o.put("favorites", new JSONArray(favorites()));
        o.put("hidden", new JSONArray(hidden()));
        o.put("locked", new JSONArray(locked()));
        o.put("pin", sp.getString("pin", ""));
        o.put("usage", usageJson());
        return o;
    }

    void fromJson(JSONObject o) {
        SharedPreferences.Editor e = sp.edit();
        e.putInt("theme", o.optInt("theme", 0));
        e.putInt("columns", o.optInt("columns", 5));
        e.putBoolean("labels", o.optBoolean("labels", true));
        e.putString("sort", o.optString("sort", "az"));
        e.putInt("dim", o.optInt("dim", 30));
        String mode = o.optString("wp_mode", o.optBoolean("wall_img", false) ? WP_CUSTOM : WP_AUTO);
        e.putString("wp_mode", mode);
        e.putInt("wp_builtin", o.optInt("wp_builtin", 0));
        e.putBoolean("boot", o.optBoolean("boot", false));
        e.putBoolean("guard", o.optBoolean("guard", true));
        e.putString("fondos_repo", o.optString("fondos_repo", OnlineWallpapers.DEFAULT_REPO));
        e.putBoolean("auto_update", o.optBoolean("auto_update", true));
        e.putBoolean("auto_clean", o.optBoolean("auto_clean", false));
        e.putInt("clock_style", o.optInt("clock_style", 0));
        e.putInt("icon_size", o.optInt("icon_size", 1));
        e.putBoolean("weather", o.optBoolean("weather", true));
        e.putBoolean("recents_on", o.optBoolean("recents_on", true));
        e.putString("favorites", arr(o, "favorites"));
        e.putString("hidden", arr(o, "hidden"));
        e.putString("locked", arr(o, "locked"));
        e.putString("pin", o.optString("pin", ""));
        JSONObject u = o.optJSONObject("usage");
        e.putString("usage", u == null ? "{}" : u.toString());
        e.putBoolean("fav_init", true);
        e.apply();
    }

    private static String arr(JSONObject o, String k) {
        JSONArray a = o.optJSONArray(k);
        return a == null ? "[]" : a.toString();
    }

    private List<String> list(String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                String s = a.optString(i, "");
                if (!s.isEmpty() && !out.contains(s)) out.add(s);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    private void putList(String key, Collection<String> v) {
        sp.edit().putString(key, new JSONArray(new ArrayList<>(v)).toString()).apply();
    }

    static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(("flowlauncher:" + s).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }
}
