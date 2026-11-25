package py.crz.flowlauncher;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bluetooth rápido: dispositivos conectados, emparejados y vinculación de nuevos. */
public class BluetoothActivity extends BaseActivity {

    private static final int REQ_PERMS = 41;
    private static final int REQ_ENABLE = 42;
    private static final int PROFILE_HID = 4; // BluetoothProfile.HID_HOST (oculto en el SDK)

    private BluetoothAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> connected = new HashSet<>();
    private final Map<String, BluetoothDevice> found = new LinkedHashMap<>();
    private final List<BluetoothProfile> proxies = new ArrayList<>();
    private boolean scanning;
    private String pairingAddress;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(a)) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d != null && safeBond(d) != BluetoothDevice.BOND_BONDED) found.put(d.getAddress(), d);
                rerender();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(a)) {
                scanning = false;
                rerender();
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(a)) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int st = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                if (d != null && d.getAddress().equals(pairingAddress)) {
                    if (st == BluetoothDevice.BOND_BONDED) {
                        Ui.toast(BluetoothActivity.this, "Vinculado: " + name(d));
                        found.remove(d.getAddress());
                        pairingAddress = null;
                    } else if (st == BluetoothDevice.BOND_NONE) {
                        Ui.toast(BluetoothActivity.this, "No se pudo vincular " + name(d));
                        pairingAddress = null;
                    }
                }
                refreshConnected();
            } else {
                // Conexión, desconexión o cambio de estado del Bluetooth
                refreshConnected();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm == null ? null : bm.getAdapter();
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, 2 /* RECEIVER_EXPORTED */);
            else registerReceiver(receiver, f);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!hasPermissions()) requestPerms();
        else refreshConnected();
        render(focusedRow());
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {
        }
        stopScan();
        closeProxies();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ Permisos

    private String[] neededPerms() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{"android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN"};
        }
        return new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT < 23) return true;
        for (String p : neededPerms()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                // En Android 6-11 la ubicación solo hace falta para buscar; listar funciona igual
                if (Build.VERSION.SDK_INT < 31) continue;
                return false;
            }
        }
        return true;
    }

    private boolean canScan() {
        if (Build.VERSION.SDK_INT < 23) return true;
        for (String p : neededPerms()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void requestPerms() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(neededPerms(), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        refreshConnected();
        render(focusedRow());
    }

    // ------------------------------------------------------------------ Estado

    /** ¿Está conectado ahora? (método interno de Android, con respaldo por perfiles) */
    static boolean isConnected(BluetoothDevice d) {
        try {
            Method m = d.getClass().getMethod("isConnected");
            Object r = m.invoke(d);
            if (r instanceof Boolean) return (Boolean) r;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Cantidad de dispositivos conectados (para el botón del inicio). */
    static int connectedCount(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && c.checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
                    != PackageManager.PERMISSION_GRANTED) return -1;
            BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter a = bm == null ? null : bm.getAdapter();
            if (a == null || !a.isEnabled()) return 0;
            int n = 0;
            for (BluetoothDevice d : a.getBondedDevices()) if (isConnected(d)) n++;
            return n;
        } catch (Throwable e) {
            return -1;
        }
    }

    private void refreshConnected() {
        if (adapter == null) return;
        try {
            if (!adapter.isEnabled()) {
                connected.clear();
                rerender();
                return;
            }
            for (BluetoothDevice d : adapter.getBondedDevices()) {
                if (isConnected(d)) connected.add(d.getAddress());
                else connected.remove(d.getAddress());
            }
            closeProxies();
            int[] profiles = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET, PROFILE_HID};
            for (int p : profiles) {
                adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        proxies.add(proxy);
                        try {
                            for (BluetoothDevice d : proxy.getConnectedDevices()) connected.add(d.getAddress());
                        } catch (Throwable ignored) {
                        }
                        rerender();
                    }

                    @Override
                    public void onServiceDisconnected(int profile) {
                    }
                }, p);
            }
        } catch (Throwable ignored) {
        }
        rerender();
    }

    private void closeProxies() {
        if (adapter == null) return;
        for (BluetoothProfile p : proxies) {
            try {
                int type = p instanceof android.bluetooth.BluetoothA2dp ? BluetoothProfile.A2DP
                        : p instanceof android.bluetooth.BluetoothHeadset ? BluetoothProfile.HEADSET : PROFILE_HID;
                adapter.closeProfileProxy(type, p);
            } catch (Throwable ignored) {
            }
        }
        proxies.clear();
    }

    private void rerender() {
        handler.removeCallbacks(rerenderTask);
        handler.postDelayed(rerenderTask, 250);
    }

    private final Runnable rerenderTask = () -> {
        if (!isFinishing()) render(focusedRow());
    };

    // ------------------------------------------------------------------ Pantalla

    private void render(int focus) {
        clear();
        header("Bluetooth", "Conectá auriculares, parlantes, teclados o joysticks con el control");
        if (adapter == null) {
            section("No disponible");
            row("Este equipo no tiene Bluetooth", null, null);
            focusRow(0);
            return;
        }
        if (Build.VERSION.SDK_INT >= 31 && !hasPermissions()) {
            section("Permiso necesario");
            row("Permitir el acceso a Bluetooth", "Tocá para dar permiso a Flow Launcher", this::requestPerms);
            focusRow(0);
            return;
        }
        boolean on;
        try {
            on = adapter.isEnabled();
        } catch (Throwable e) {
            on = false;
        }
        section("Estado");
        row("Bluetooth", on ? "Encendido  ·  tocá para apagarlo" : "Apagado  ·  tocá para encenderlo",
                () -> toggleBluetooth());
        if (!on) {
            focusRow(focus);
            return;
        }

        List<BluetoothDevice> bonded = new ArrayList<>();
        try {
            bonded.addAll(adapter.getBondedDevices());
        } catch (Throwable ignored) {
        }
        List<BluetoothDevice> conn = new ArrayList<>(), rest = new ArrayList<>();
        for (BluetoothDevice d : bonded) (connected.contains(d.getAddress()) ? conn : rest).add(d);

        section("Conectados ahora (" + conn.size() + ")");
        if (conn.isEmpty()) row("Ningún dispositivo conectado", null, null);
        for (BluetoothDevice d : conn) {
            row(icon(d) + "  " + name(d), "Conectado  ·  " + type(d), () -> deviceMenu(d, true));
        }

        section("Emparejados (" + rest.size() + ")");
        if (rest.isEmpty()) row("No hay otros dispositivos emparejados", null, null);
        for (BluetoothDevice d : rest) {
            row(icon(d) + "  " + name(d), "No conectado  ·  " + type(d)
                    + "  ·  encendelo para que se conecte solo", () -> deviceMenu(d, false));
        }

        section("Vincular un dispositivo nuevo");
        row(scanning ? "Buscando dispositivos…  (tocá para detener)" : "Buscar dispositivos cercanos",
                "Poné el auricular, parlante o joystick en modo de vinculación",
                () -> {
                    if (scanning) stopScan();
                    else startScan();
                    render(focusedRow());
                });
        for (BluetoothDevice d : found.values()) {
            boolean pairing = d.getAddress().equals(pairingAddress);
            row(icon(d) + "  " + name(d), pairing ? "Vinculando… aceptá en el dispositivo si lo pide"
                    : "Tocá para vincular  ·  " + type(d), () -> pair(d));
        }
        row("Ajustes de Bluetooth de Android", "Opciones avanzadas del sistema",
                () -> AppRepository.openSystemScreen(this, Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_SETTINGS));
        focusRow(focus);
    }

    @SuppressWarnings("deprecation")
    private void toggleBluetooth() {
        try {
            if (adapter.isEnabled()) {
                if (Build.VERSION.SDK_INT >= 33 || !adapter.disable()) {
                    AppRepository.openSystemScreen(this, Settings.ACTION_BLUETOOTH_SETTINGS);
                }
            } else {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE);
            }
        } catch (Throwable e) {
            AppRepository.openSystemScreen(this, Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_SETTINGS);
        }
        handler.postDelayed(() -> render(focusedRow()), 1500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ENABLE) handler.postDelayed(this::refreshConnected, 1200);
    }

    // ------------------------------------------------------------------ Búsqueda y vinculación

    private void startScan() {
        if (!canScan()) {
            requestPerms();
            Ui.toast(this, "Dale el permiso y volvé a tocar «Buscar»");
            return;
        }
        try {
            found.clear();
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            scanning = adapter.startDiscovery();
            if (!scanning) Ui.toast(this, "No se pudo iniciar la búsqueda");
        } catch (Throwable e) {
            scanning = false;
            Ui.toast(this, "No se pudo buscar: " + e.getMessage());
        }
    }

    private void stopScan() {
        try {
            if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery();
        } catch (Throwable ignored) {
        }
        scanning = false;
    }

    private void pair(BluetoothDevice d) {
        stopScan();
        try {
            pairingAddress = d.getAddress();
            if (!d.createBond()) {
                pairingAddress = null;
                Ui.toast(this, "No se pudo iniciar la vinculación");
            } else {
                Ui.toast(this, "Vinculando con " + name(d) + "…");
            }
        } catch (Throwable e) {
            pairingAddress = null;
            Ui.toast(this, "Error al vincular: " + e.getMessage());
        }
        render(focusedRow());
    }

    private void deviceMenu(BluetoothDevice d, boolean isConnected) {
        new AlertDialog.Builder(this)
                .setTitle(name(d))
                .setMessage(type(d) + "\n" + (isConnected ? "Conectado" : "Emparejado, no conectado")
                        + "\n\nSi es el control remoto de la Flow Box, no lo olvides.")
                .setPositiveButton("Olvidar dispositivo", (x, w) -> confirmForget(d))
                .setNeutralButton("Ajustes de Bluetooth",
                        (x, w) -> AppRepository.openSystemScreen(this, Settings.ACTION_BLUETOOTH_SETTINGS))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void confirmForget(BluetoothDevice d) {
        new AlertDialog.Builder(this)
                .setTitle("¿Olvidar " + name(d) + "?")
                .setMessage("Para volver a usarlo tendrás que vincularlo de nuevo.")
                .setPositiveButton("Olvidar", (x, w) -> {
                    boolean ok = false;
                    try {
                        Method m = d.getClass().getMethod("removeBond");
                        Object r = m.invoke(d);
                        ok = r instanceof Boolean && (Boolean) r;
                    } catch (Throwable ignored) {
                    }
                    if (ok) {
                        Ui.toast(this, "Dispositivo olvidado");
                        connected.remove(d.getAddress());
                        handler.postDelayed(() -> render(focusedRow()), 800);
                    } else {
                        Ui.toast(this, "Este equipo no lo permite desde aquí; se abren los ajustes de Bluetooth");
                        AppRepository.openSystemScreen(this, Settings.ACTION_BLUETOOTH_SETTINGS);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------------ Textos

    private static int safeBond(BluetoothDevice d) {
        try {
            return d.getBondState();
        } catch (Throwable e) {
            return BluetoothDevice.BOND_NONE;
        }
    }

    private static String name(BluetoothDevice d) {
        try {
            String n = d.getName();
            if (n != null && !n.trim().isEmpty()) return n.trim();
        } catch (Throwable ignored) {
        }
        return "Dispositivo " + d.getAddress();
    }

    private static int major(BluetoothDevice d) {
        try {
            BluetoothClass c = d.getBluetoothClass();
            return c == null ? -1 : c.getMajorDeviceClass();
        } catch (Throwable e) {
            return -1;
        }
    }

    private static String icon(BluetoothDevice d) {
        switch (major(d)) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO: return "🎧";
            case BluetoothClass.Device.Major.PERIPHERAL: return "🎮";
            case BluetoothClass.Device.Major.PHONE: return "📱";
            case BluetoothClass.Device.Major.COMPUTER: return "💻";
            default: return "🔵";
        }
    }

    private static String type(BluetoothDevice d) {
        switch (major(d)) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO: return "Audio (auricular o parlante)";
            case BluetoothClass.Device.Major.PERIPHERAL: return "Control, teclado o mouse";
            case BluetoothClass.Device.Major.PHONE: return "Teléfono";
            case BluetoothClass.Device.Major.COMPUTER: return "Computadora";
            default: return "Otro dispositivo";
        }
    }
}
