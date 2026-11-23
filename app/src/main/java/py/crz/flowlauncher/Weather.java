package py.crz.flowlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Clima desde Open-Meteo (gratis, sin cuenta ni clave). */
final class Weather {
    private Weather() {}

    static final class Place {
        String name, detail;
        double lat, lon;
    }

    static final class Now {
        int temp, max, min, code;
        String city;
        long time;
    }

    // ---- Ciudad elegida ----
    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("flow_weather", Context.MODE_PRIVATE);
    }

    static Place place(Context c) {
        SharedPreferences s = sp(c);
        Place p = new Place();
        p.name = s.getString("name", "Asunción");
        p.lat = Double.longBitsToDouble(s.getLong("lat", Double.doubleToLongBits(-25.2867)));
        p.lon = Double.longBitsToDouble(s.getLong("lon", Double.doubleToLongBits(-57.6470)));
        return p;
    }

    static void setPlace(Context c, Place p) {
        sp(c).edit().putString("name", p.name)
                .putLong("lat", Double.doubleToLongBits(p.lat))
                .putLong("lon", Double.doubleToLongBits(p.lon))
                .remove("cache").apply();
    }

    /** Último clima guardado (sirve sin internet). */
    static Now cached(Context c) {
        try {
            String js = sp(c).getString("cache", null);
            if (js == null) return null;
            JSONObject o = new JSONObject(js);
            Now n = new Now();
            n.temp = o.getInt("t");
            n.max = o.getInt("mx");
            n.min = o.getInt("mn");
            n.code = o.getInt("c");
            n.city = o.getString("city");
            n.time = o.getLong("time");
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    /** Descarga el clima actual (llamar fuera del hilo principal). */
    static Now fetch(Context c) throws Exception {
        Place p = place(c);
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto&forecast_days=1", p.lat, p.lon);
        JSONObject o = new JSONObject(get(url));
        JSONObject cur = o.getJSONObject("current");
        JSONObject daily = o.getJSONObject("daily");
        Now n = new Now();
        n.temp = (int) Math.round(cur.getDouble("temperature_2m"));
        n.code = cur.getInt("weather_code");
        n.max = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        n.min = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        n.city = p.name;
        n.time = System.currentTimeMillis();
        JSONObject save = new JSONObject();
        save.put("t", n.temp).put("mx", n.max).put("mn", n.min).put("c", n.code)
                .put("city", n.city).put("time", n.time);
        sp(c).edit().putString("cache", save.toString()).apply();
        return n;
    }

    /** Busca ciudades por nombre. */
    static List<Place> search(String name) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=es&format=json&name="
                + URLEncoder.encode(name.trim(), "UTF-8");
        JSONObject o = new JSONObject(get(url));
        List<Place> out = new ArrayList<>();
        JSONArray arr = o.optJSONArray("results");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.getJSONObject(i);
            Place p = new Place();
            p.name = r.optString("name");
            String a1 = r.optString("admin1", ""), country = r.optString("country", "");
            p.detail = (a1.isEmpty() ? "" : a1 + ", ") + country;
            p.lat = r.getDouble("latitude");
            p.lon = r.getDouble("longitude");
            out.add(p);
        }
        return out;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(12000);
        con.setReadTimeout(15000);
        con.setRequestProperty("User-Agent", "FlowLauncher");
        try (InputStream in = con.getInputStream()) {
            return Backup.readAll(in);
        } finally {
            con.disconnect();
        }
    }

    /** Ícono y texto según el código WMO del clima. */
    static String icon(int code) {
        if (code == 0) return "☀️";
        if (code <= 2) return "⛅";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 95) return "⛈️";
        if (code >= 71 && code <= 86 && code != 80 && code != 81 && code != 82) return "❄️";
        if (code >= 51) return "🌧️";
        return "🌡️";
    }

    static String text(int code) {
        if (code == 0) return "Despejado";
        if (code == 1) return "Mayormente despejado";
        if (code == 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code == 45 || code == 48) return "Niebla";
        if (code >= 51 && code <= 57) return "Llovizna";
        if (code >= 61 && code <= 67) return "Lluvia";
        if (code >= 71 && code <= 77) return "Nieve";
        if (code >= 80 && code <= 82) return "Chaparrones";
        if (code >= 95) return "Tormenta";
        return "";
    }
}
