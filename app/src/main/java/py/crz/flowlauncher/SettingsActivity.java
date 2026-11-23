package py.crz.flowlauncher;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ajustes del launcher. */
public class SettingsActivity extends BaseActivity {

    private static final int REQ_PERM_IMAGES = 10;
    private static final int REQ_PICK_IMAGE = 11;
    private static final int REQ_EXPORT = 12;
    private static final int REQ_IMPORT = 13;
    private static final int REQ_PERM_WRITE = 14;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(focusedRow());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void render(int focus) {
        t = LauncherTheme.get(prefs.theme());
        root.setBackground(Ui.background(t));
        clear();
        header("Ajustes", "Flow Launcher " + version() + "  ·  Presioná Atrás para volver al inicio");

        section("Apariencia");
        row("Tema de colores", t.name, this::chooseTheme);
        row("Fondo de pantalla", prefs.wallpaperImage() ? "Imagen personalizada" : "Colores del tema",
                this::chooseWallpaper);
        row("Oscurecer el fondo", prefs.dim() + "%  (solo con imagen personalizada)", this::chooseDim);
        row("Columnas de aplicaciones", prefs.columns() + " por fila", this::chooseColumns);
        row("Nombres de las apps", prefs.alwaysLabels() ? "Siempre visibles" : "Solo en la app seleccionada",
                () -> {
                    prefs.setAlwaysLabels(!prefs.alwaysLabels());
                    render(focusedRow());
                });
        row("Orden de las apps", "usage".equals(prefs.sort()) ? "Más usadas primero" : "Alfabético (A-Z)",
                () -> {
                    prefs.setSort("usage".equals(prefs.sort()) ? "az" : "usage");
                    render(focusedRow());
                });

        section("Aplicaciones");
        row("Apps ocultas", prefs.hidden().size() + " oculta(s)", () -> chooseApps(false));
        row("Apps bloqueadas con PIN", prefs.locked().size() + " bloqueada(s)", () -> {
            if (!prefs.hasPin()) {
                Ui.toast(this, "Primero creá un PIN");
                PinDialogs.create(this, prefs, () -> chooseApps(true));
            } else {
                chooseApps(true);
            }
        });
        row("Administrador de aplicaciones", "Ver todas, abrir información o desinstalar",
                () -> startActivity(new Intent(this, AppManagerActivity.class)));

        section("Seguridad");
        row("PIN de seguridad", prefs.hasPin()
                ? "Activado: protege Ajustes, Mantenimiento y las apps bloqueadas"
                : "Desactivado", this::pinMenu);

        section("Copias de seguridad");
        row("Guardar copia en Descargas", "Incluye tema, favoritos, apps ocultas, PIN y fondo",
                this::exportToDownloads);
        row("Exportar copia a un archivo…", "Elegí la ubicación (pendrive USB, almacenamiento interno)",
                this::exportSaf);
        row("Importar copia desde un archivo…", "Restaura una copia .json de Flow Launcher",
                this::importSaf);
        row("Copia rápida interna", Backup.internalFile(this).exists()
                ? "Hay una copia guardada en el equipo" : "Todavía no hay copia interna", this::internalMenu);

        section("Equipo");
        row("Centro de mantenimiento", "Memoria RAM, almacenamiento, limpieza y estado del equipo",
                () -> startActivity(new Intent(this, MaintenanceActivity.class)));
        row("Abrir Flow Launcher al encender", prefs.bootStart() ? "Sí" : "No", () -> {
            prefs.setBootStart(!prefs.bootStart());
            render(focusedRow());
        });
        row("Elegir launcher predeterminado", "Para que el botón Inicio abra Flow Launcher",
                this::defaultLauncher);
        row("Ajustes de Android", "Red, sonido, pantalla, fecha y hora",
                () -> AppRepository.openSystemScreen(this, Settings.ACTION_SETTINGS));

        section("Acerca de");
        row("Flow Launcher " + version(), "Launcher para Flow Box F1 · Hecho a medida", this::about);

        focusRow(focus);
    }

    private String version() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return "v" + pi.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ Apariencia

    private void chooseTheme() {
        new AlertDialog.Builder(this)
                .setTitle("Tema de colores")
                .setSingleChoiceItems(LauncherTheme.names(), prefs.theme(), (d, w) -> {
                    prefs.setTheme(w);
                    d.dismiss();
                    render(focusedRow());
                })
                .show();
    }

    private void chooseWallpaper() {
        String[] items = {"Usar colores del tema", "Elegir imagen de la galería / USB",
                "Elegir imagen con el explorador de archivos"};
        new AlertDialog.Builder(this)
                .setTitle("Fondo de pantalla")
                .setItems(items, (d, w) -> {
                    if (w == 0) {
                        prefs.setWallpaperImage(false);
                        render(focusedRow());
                    } else if (w == 1) {
                        pickFromGallery();
                    } else {
                        pickWithFileManager();
                    }
                })
                .show();
    }

    private void chooseDim() {
        final int[] values = {0, 15, 30, 45, 60, 75};
        String[] labels = new String[values.length];
        int sel = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i] == 0 ? "Sin oscurecer" : values[i] + "%";
            if (values[i] == prefs.dim()) sel = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Oscurecer el fondo")
                .setSingleChoiceItems(labels, sel, (d, w) -> {
                    prefs.setDim(values[w]);
                    d.dismiss();
                    render(focusedRow());
                })
                .show();
    }

    private void chooseColumns() {
        final int[] values = {3, 4, 5, 6, 7, 8};
        String[] labels = new String[values.length];
        int sel = 2;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i] + " por fila" + (values[i] == 5 ? " (recomendado)" : "");
            if (values[i] == prefs.columns()) sel = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Columnas de aplicaciones")
                .setSingleChoiceItems(labels, sel, (d, w) -> {
                    prefs.setColumns(values[w]);
                    d.dismiss();
                    render(focusedRow());
                })
                .show();
    }

    // ---- Fondo: galería (MediaStore) — funciona aunque el equipo no tenga explorador de archivos

    private String imagePermission() {
        return Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_IMAGES"
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private void pickFromGallery() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(imagePermission()) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{imagePermission()}, REQ_PERM_IMAGES);
            return;
        }
        executor.execute(() -> {
            final List<String> names = new ArrayList<>();
            final List<Uri> uris = new ArrayList<>();
            Cursor c = null;
            try {
                Uri base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                c = getContentResolver().query(base,
                        new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME},
                        null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
                while (c != null && c.moveToNext() && uris.size() < 200) {
                    long id = c.getLong(0);
                    String n = c.getString(1);
                    uris.add(ContentUris.withAppendedId(base, id));
                    names.add(n == null ? "Imagen " + id : n);
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
            }
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (uris.isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setTitle("No se encontraron imágenes")
                            .setMessage("Copiá fotos al equipo (o conectá un pendrive USB con imágenes) "
                                    + "e intentá de nuevo.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Elegí una imagen")
                        .setItems(names.toArray(new String[0]), (d, w) -> applyWallpaper(uris.get(w)))
                        .show();
            });
        });
    }

    private void pickWithFileManager() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_PICK_IMAGE);
        } catch (Exception e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT);
            g.addCategory(Intent.CATEGORY_OPENABLE);
            g.setType("image/*");
            try {
                startActivityForResult(g, REQ_PICK_IMAGE);
            } catch (Exception e2) {
                Ui.toast(this, "Este equipo no tiene explorador de archivos. Usá la opción de galería.");
            }
        }
    }

    private void applyWallpaper(final Uri uri) {
        Ui.toast(this, "Aplicando fondo…");
        executor.execute(() -> {
            final boolean ok = Wallpaper.saveFromUri(getApplicationContext(), uri);
            runOnUiThread(() -> {
                if (ok) {
                    prefs.setWallpaperImage(true);
                    Ui.toast(this, "Fondo de pantalla aplicado");
                } else {
                    Ui.toast(this, "No se pudo usar esa imagen");
                }
                if (!isFinishing()) render(focusedRow());
            });
        });
    }

    // ------------------------------------------------------------------ Apps ocultas / bloqueadas

    private void chooseApps(final boolean lockedMode) {
        executor.execute(() -> {
            final List<AppItem> list = AppRepository.load(getApplicationContext(), false);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                final Set<String> current = lockedMode ? prefs.locked() : prefs.hidden();
                final String[] labels = new String[list.size()];
                final boolean[] checked = new boolean[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    labels[i] = list.get(i).label;
                    checked[i] = current.contains(list.get(i).pkg);
                }
                new AlertDialog.Builder(this)
                        .setTitle(lockedMode ? "Apps que piden PIN" : "Apps ocultas")
                        .setMultiChoiceItems(labels, checked, (d, w, isChecked) -> checked[w] = isChecked)
                        .setPositiveButton("Guardar", (d, w) -> {
                            Set<String> out = new HashSet<>();
                            for (int i = 0; i < list.size(); i++) if (checked[i]) out.add(list.get(i).pkg);
                            // Conserva las que ya estaban marcadas pero hoy no figuran (apps desinstaladas)
                            for (String p : current) {
                                boolean present = false;
                                for (AppItem a : list) if (a.pkg.equals(p)) present = true;
                                if (!present) out.add(p);
                            }
                            if (lockedMode) prefs.setLocked(out);
                            else prefs.setHidden(out);
                            render(focusedRow());
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            });
        });
    }

    // ------------------------------------------------------------------ PIN

    private void pinMenu() {
        if (!prefs.hasPin()) {
            PinDialogs.create(this, prefs, () -> render(focusedRow()));
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("PIN de seguridad")
                .setItems(new String[]{"Cambiar PIN", "Quitar PIN"}, (d, w) -> {
                    if (w == 0) {
                        PinDialogs.require(this, prefs, "PIN actual",
                                () -> PinDialogs.create(this, prefs, () -> render(focusedRow())));
                    } else {
                        PinDialogs.require(this, prefs, "PIN actual", () -> {
                            prefs.setPin(null);
                            Ui.toast(this, "PIN quitado");
                            render(focusedRow());
                        });
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ Copias de seguridad

    private void exportToDownloads() {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM_WRITE);
            return;
        }
        executor.execute(() -> {
            String msg;
            try {
                msg = "Copia guardada en " + Backup.saveToDownloads(getApplicationContext(), prefs);
            } catch (Exception e) {
                msg = "No se pudo guardar: " + e.getMessage();
            }
            final String m = msg;
            runOnUiThread(() -> Ui.toast(this, m));
        });
    }

    private void exportSaf() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, Backup.fileName());
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            Ui.toast(this, "Este equipo no tiene explorador de archivos. Usá «Guardar copia en Descargas».");
        }
    }

    private void importSaf() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain",
                "application/octet-stream"});
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            Ui.toast(this, "Este equipo no tiene explorador de archivos para importar.");
        }
    }

    private void internalMenu() {
        boolean exists = Backup.internalFile(this).exists();
        String[] items = exists ? new String[]{"Guardar copia ahora (reemplaza la anterior)", "Restaurar esta copia"}
                : new String[]{"Guardar copia ahora"};
        new AlertDialog.Builder(this)
                .setTitle("Copia rápida interna")
                .setMessage(null)
                .setItems(items, (d, w) -> {
                    if (w == 0) {
                        try {
                            Backup.saveInternal(this, prefs);
                            Ui.toast(this, "Copia interna guardada");
                        } catch (Exception e) {
                            Ui.toast(this, "No se pudo guardar: " + e.getMessage());
                        }
                        render(focusedRow());
                    } else {
                        confirmRestore(() -> {
                            try {
                                Backup.restoreInternal(this, prefs);
                                Ui.toast(this, "Configuración restaurada");
                            } catch (Exception e) {
                                Ui.toast(this, "No se pudo restaurar: " + e.getMessage());
                            }
                            render(0);
                        });
                    }
                })
                .show();
    }

    private void confirmRestore(Runnable go) {
        new AlertDialog.Builder(this)
                .setTitle("¿Restaurar la copia?")
                .setMessage("Se reemplazará la configuración actual (tema, favoritos, apps ocultas, PIN y fondo).")
                .setPositiveButton("Restaurar", (d, w) -> go.run())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_PICK_IMAGE) {
            applyWallpaper(uri);
        } else if (requestCode == REQ_EXPORT) {
            executor.execute(() -> {
                String msg;
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new Exception("sin acceso al archivo");
                    out.write(Backup.build(getApplicationContext(), prefs).getBytes("UTF-8"));
                    msg = "Copia exportada correctamente";
                } catch (Exception e) {
                    msg = "No se pudo exportar: " + e.getMessage();
                }
                final String m = msg;
                runOnUiThread(() -> Ui.toast(this, m));
            });
        } else if (requestCode == REQ_IMPORT) {
            confirmRestore(() -> {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new Exception("sin acceso al archivo");
                    Backup.restore(getApplicationContext(), prefs, Backup.readAll(in));
                    Ui.toast(this, "Configuración restaurada");
                } catch (Exception e) {
                    Ui.toast(this, "No se pudo importar: " + e.getMessage());
                }
                render(0);
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_PERM_IMAGES) {
            if (granted) pickFromGallery();
            else Ui.toast(this, "Sin permiso no se pueden listar las imágenes");
        } else if (requestCode == REQ_PERM_WRITE) {
            if (granted) exportToDownloads();
            else Ui.toast(this, "Sin permiso no se puede guardar en Descargas");
        }
    }

    // ------------------------------------------------------------------ Equipo

    private void defaultLauncher() {
        try {
            Intent i = new Intent(Settings.ACTION_HOME_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Exception ignored) {
        }
        new AlertDialog.Builder(this)
                .setTitle("Elegir launcher predeterminado")
                .setMessage("Este equipo no tiene la pantalla para elegir el launcher.\n\n"
                        + "Probá así: presioná el botón Inicio (casita) del control. Si aparece la pregunta "
                        + "«¿Qué app usar?», elegí Flow Launcher y luego «Siempre».\n\n"
                        + "Si no aparece la pregunta, el equipo tiene el launcher de fábrica bloqueado; "
                        + "en ese caso activá «Abrir Flow Launcher al encender».")
                .setPositiveButton("Entendido", null)
                .show();
    }

    private void about() {
        new AlertDialog.Builder(this)
                .setTitle("Flow Launcher " + version())
                .setMessage("Launcher para Flow Box F1 y equipos Android TV.\n\n"
                        + "Control remoto:\n"
                        + "•  OK: abrir la app\n"
                        + "•  Mantener OK o botón Menú: opciones de la app\n"
                        + "•  Atrás / Inicio: volver arriba\n\n"
                        + "Equipo: " + Build.MANUFACTURER + " " + Build.MODEL
                        + "  ·  Android " + Build.VERSION.RELEASE)
                .setPositiveButton("Cerrar", null)
                .show();
    }
}
