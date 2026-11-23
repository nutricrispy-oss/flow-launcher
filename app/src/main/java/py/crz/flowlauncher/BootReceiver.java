package py.crz.flowlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Abre Flow Launcher al encender el equipo (si está activado en Ajustes). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!new Prefs(context).bootStart()) return;
        try {
            Intent i = new Intent(context, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(i);
        } catch (Exception ignored) {
            // Algunos equipos con Android 10+ bloquean abrir pantallas al arrancar.
        }
    }
}
