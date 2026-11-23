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

    boolean wallpaperImage() { return sp.getBoolean("wall_img", false); }
    void setWallpaperImage(boolean v) { sp.edit().putBoolean("wall_img", v).apply(); }

    boolean bootStart() { return sp.getBoolean("boot", false); }
    void setBootStart(boolean v) { sp.edit().putBoolean("boot", v).apply(); }

    /** Volver siempre a Flow Launcher cuando aparece el inicio de fábrica. */
    boolean guard() { return sp.getBoolean("guard", true); }
    void setGuard(boolean v) { sp.edit().putBoolean("guard", v).apply(); }

    boolean homeHintShown() { return sp.getBoolean("home_hint", false); }
    void setHomeHintShown(boolean v) { sp.edit().putBoolean("home_hint", v).apply(); }

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
        o.put("wall_img", wallpaperImage());
        o.put("boot", bootStart());
        o.put("guard", guard());
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
        e.putBoolean("wall_img", o.optBoolean("wall_img", false));
        e.putBoolean("boot", o.optBoolean("boot", false));
        e.putBoolean("guard", o.optBoolean("guard", true));
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
