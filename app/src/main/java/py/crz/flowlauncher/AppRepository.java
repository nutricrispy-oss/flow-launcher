package py.crz.flowlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/** Lee las apps instaladas y las abre. */
final class AppRepository {

    private AppRepository() {}

    static List<AppItem> load(Context c, boolean withDrawables) {
        PackageManager pm = c.getPackageManager();
        LinkedHashMap<String, AppItem> map = new LinkedHashMap<>();
        // Primero las apps de TV (tienen banner), luego las normales
        collect(c, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, withDrawables, map);
        collect(c, pm, Intent.CATEGORY_LAUNCHER, false, withDrawables, map);
        List<AppItem> list = new ArrayList<>(map.values());
        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }

    private static void collect(Context c, PackageManager pm, String category, boolean leanback,
                                boolean withDrawables, LinkedHashMap<String, AppItem> map) {
        List<ResolveInfo> infos;
        try {
            infos = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
        } catch (Exception e) {
            return;
        }
        for (ResolveInfo ri : infos) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null) continue;
            String pkg = ai.packageName;
            if (pkg.equals(c.getPackageName()) || map.containsKey(pkg)) continue;
            AppItem a = new AppItem();
            a.pkg = pkg;
            a.activity = ai.name;
            a.leanback = leanback;
            CharSequence l = null;
            try {
                l = ri.loadLabel(pm);
            } catch (Exception ignored) {
            }
            a.label = l == null ? pkg : l.toString().trim();
            a.system = ai.applicationInfo != null
                    && (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (withDrawables) {
                try {
                    a.banner = ai.loadBanner(pm);
                    if (a.banner == null && ai.applicationInfo != null) {
                        a.banner = ai.applicationInfo.loadBanner(pm);
                    }
                } catch (Exception ignored) {
                }
                try {
                    a.icon = ri.loadIcon(pm);
                } catch (Exception ignored) {
                }
            }
            map.put(pkg, a);
        }
    }

    static boolean launch(Context c, AppItem a) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setClassName(a.pkg, a.activity);
        i.addCategory(a.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return launchPackage(c, a.pkg);
        }
    }

    static boolean launchPackage(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent j = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (j == null) j = pm.getLaunchIntentForPackage(pkg);
        if (j == null) return false;
        j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(j);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isLaunchable(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        return pm.getLeanbackLaunchIntentForPackage(pkg) != null
                || pm.getLaunchIntentForPackage(pkg) != null;
    }

    static void openAppInfo(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            Ui.toast(c, "Este equipo no permite abrir la información de la app");
        }
    }

    static void uninstall(Context c, String pkg) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            Ui.toast(c, "No se pudo iniciar la desinstalación");
        }
    }

    /** Abre una pantalla de Ajustes de Android; si no existe, prueba la siguiente. */
    static void openSystemScreen(Context c, String... actions) {
        for (String action : actions) {
            try {
                Intent i = new Intent(action);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return;
            } catch (Exception ignored) {
            }
        }
        Ui.toast(c, "Este equipo no tiene esa pantalla de ajustes");
    }
}
