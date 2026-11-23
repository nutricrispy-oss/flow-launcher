package py.crz.flowlauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guardián del inicio: cuando el equipo muestra su launcher de fábrica
 * (por el botón Inicio o Salir), vuelve a abrir Flow Launcher.
 * Sirve en equipos de operadora que no dejan cambiar el launcher predeterminado.
 */
public class HomeGuardService extends AccessibilityService {

    private final Set<String> homePackages = new HashSet<>();
    private final Set<String> homeClasses = new HashSet<>();
    private long lastBounce;
    private long lastScan;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        scanHomes();
    }

    /** Busca los launchers instalados (todos menos este). */
    private void scanHomes() {
        homePackages.clear();
        homeClasses.clear();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> list = getPackageManager().queryIntentActivities(home, 0);
            for (ResolveInfo ri : list) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                homePackages.add(pkg);
                homeClasses.add(ri.activityInfo.name);
                if (ri.activityInfo.targetActivity != null) homeClasses.add(ri.activityInfo.targetActivity);
            }
        } catch (Exception ignored) {
        }
        lastScan = SystemClock.elapsedRealtime();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!new Prefs(this).guard()) return;
        CharSequence pkgCs = event.getPackageName();
        CharSequence clsCs = event.getClassName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        String cls = clsCs == null ? "" : clsCs.toString();
        if (SystemClock.elapsedRealtime() - lastScan > 60000) scanHomes();
        if (!homePackages.contains(pkg)) return;
        // Solo la pantalla de inicio de fábrica, no otras pantallas de esa misma app (ej. TV en vivo)
        boolean isHomeScreen = homeClasses.contains(cls)
                || (cls.startsWith(".") && homeClasses.contains(pkg + cls));
        if (!isHomeScreen) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastBounce < 1200) return;
        lastBounce = now;
        try {
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onInterrupt() {
    }

    /** ¿Está activado el guardián en Accesibilidad? */
    static boolean isEnabled(Context c) {
        try {
            String list = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (list == null) return false;
            ComponentName me = new ComponentName(c, HomeGuardService.class);
            TextUtils.SimpleStringSplitter sp = new TextUtils.SimpleStringSplitter(':');
            sp.setString(list);
            while (sp.hasNext()) {
                ComponentName cn = ComponentName.unflattenFromString(sp.next());
                if (me.equals(cn)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** ¿Flow Launcher es el launcher predeterminado del sistema? */
    static boolean isDefaultHome(Context c) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = c.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    static void openAccessibilitySettings(Context c) {
        AppRepository.openSystemScreen(c, Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS);
    }
}
