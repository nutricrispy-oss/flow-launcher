package py.crz.flowlauncher;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.util.List;

/** Liberación de memoria RAM (cierra apps que quedaron en segundo plano). */
final class Cleaner {
    private Cleaner() {}

    static long availRam(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    /** Devuelve cuántas apps revisó. Llamar fuera del hilo principal. */
    static int killBackground(Context c) {
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
        return count;
    }
}
