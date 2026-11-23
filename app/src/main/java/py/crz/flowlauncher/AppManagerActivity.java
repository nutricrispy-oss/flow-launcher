package py.crz.flowlauncher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lista de todas las apps instaladas con acciones rápidas. */
public class AppManagerActivity extends BaseActivity {

    private static final class Row {
        String pkg, label, version;
        boolean system, updatedSystem;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Row> all = new ArrayList<>();
    private final List<Row> shown = new ArrayList<>();
    private final Map<String, Drawable> icons = new HashMap<>();
    private int filter = 0; // 0 = instaladas por mí, 1 = del sistema, 2 = todas
    private ListView list;
    private TextView countView;
    private LinearLayout filterBox;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int h = Ui.dp(this, 64), v = Ui.dp(this, 28);
        page.setPadding(h, v, h, v);
        page.setClipChildren(false);

        TextView title = Ui.text(this, "Administrador de aplicaciones", 30, t.text, true);
        page.addView(title);
        countView = Ui.text(this, "Cargando…", 15, t.subtext, false);
        page.addView(countView);

        filterBox = new LinearLayout(this);
        filterBox.setOrientation(LinearLayout.HORIZONTAL);
        filterBox.setClipChildren(false);
        filterBox.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 10));
        page.addView(filterBox);

        list = new ListView(this);
        list.setDivider(null);
        list.setItemsCanFocus(false);
        list.setSelector(Ui.rounded(t.accent, Ui.dp(this, 10), 0, 0));
        list.setDrawSelectorOnTop(false);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, view, pos, id) -> showActions(shown.get(pos)));
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(page, new FrameLayout.LayoutParams(-1, -1));
        buildFilters();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildFilters() {
        filterBox.removeAllViews();
        String[] names = {"Instaladas por mí", "Del sistema", "Todas"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView b = Ui.button(this, t, (filter == i ? "● " : "") + names[i], () -> {
                filter = idx;
                buildFilters();
                applyFilter();
                filterBox.getChildAt(idx).requestFocus();
            });
            ((LinearLayout.LayoutParams) b.getLayoutParams()).leftMargin = i == 0 ? 0 : Ui.dp(this, 10);
            filterBox.addView(b);
        }
    }

    private void load() {
        final Context c = getApplicationContext();
        executor.execute(() -> {
            PackageManager pm = c.getPackageManager();
            final List<Row> rows = new ArrayList<>();
            try {
                for (PackageInfo pi : pm.getInstalledPackages(0)) {
                    ApplicationInfo ai = pi.applicationInfo;
                    if (ai == null) continue;
                    Row r = new Row();
                    r.pkg = pi.packageName;
                    CharSequence l = pm.getApplicationLabel(ai);
                    r.label = l == null ? r.pkg : l.toString();
                    r.version = pi.versionName == null ? "" : pi.versionName;
                    r.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    r.updatedSystem = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                    rows.add(r);
                }
            } catch (Exception ignored) {
            }
            Collections.sort(rows, (a, b) -> a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                all.clear();
                all.addAll(rows);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        shown.clear();
        int user = 0, sys = 0;
        for (Row r : all) {
            if (r.system) sys++;
            else user++;
            if (filter == 2 || (filter == 0 && !r.system) || (filter == 1 && r.system)) shown.add(r);
        }
        countView.setText(user + " instaladas por vos  ·  " + sys + " del sistema  ·  "
                + "OK sobre una app para ver opciones");
        adapter.notifyDataSetChanged();
    }

    private void showActions(final Row r) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final boolean launchable = AppRepository.isLaunchable(this, r.pkg);
        if (launchable) {
            labels.add("Abrir");
            actions.add(() -> AppRepository.launchPackage(this, r.pkg));
            final List<String> favs = prefs.favorites();
            if (!favs.contains(r.pkg)) {
                labels.add("Agregar a favoritos");
                actions.add(() -> {
                    favs.add(r.pkg);
                    prefs.setFavorites(favs);
                    Ui.toast(this, r.label + " agregada a favoritos");
                });
            }
        }
        labels.add("Información (borrar caché, forzar detención, permisos)");
        actions.add(() -> AppRepository.openAppInfo(this, r.pkg));
        if (!r.system) {
            labels.add("Desinstalar");
            actions.add(() -> AppRepository.uninstall(this, r.pkg));
        } else if (r.updatedSystem) {
            labels.add("Desinstalar actualizaciones");
            actions.add(() -> AppRepository.uninstall(this, r.pkg));
        }
        new AlertDialog.Builder(this)
                .setTitle(r.label)
                .setItems(labels.toArray(new String[0]), (d, w) -> actions.get(w).run())
                .show();
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Holder h;
            if (convert == null) {
                h = new Holder();
                LinearLayout row = new LinearLayout(AppManagerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int p = Ui.dp(AppManagerActivity.this, 12);
                row.setPadding(p, Ui.dp(AppManagerActivity.this, 8), p, Ui.dp(AppManagerActivity.this, 8));
                h.icon = new ImageView(AppManagerActivity.this);
                int s = Ui.dp(AppManagerActivity.this, 40);
                row.addView(h.icon, new LinearLayout.LayoutParams(s, s));
                LinearLayout col = new LinearLayout(AppManagerActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(Ui.dp(AppManagerActivity.this, 16), 0, 0, 0);
                h.name = Ui.text(AppManagerActivity.this, "", 17, t.text, true);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.END);
                h.info = Ui.text(AppManagerActivity.this, "", 13, t.subtext, false);
                h.info.setSingleLine(true);
                h.info.setEllipsize(TextUtils.TruncateAt.END);
                col.addView(h.name);
                col.addView(h.info);
                row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
                h.tag = Ui.text(AppManagerActivity.this, "", 13, t.accent, true);
                row.addView(h.tag);
                row.setTag(h);
                convert = row;
            } else {
                h = (Holder) convert.getTag();
            }
            Row r = shown.get(position);
            h.name.setText(r.label);
            h.info.setText(r.pkg + (r.version.isEmpty() ? "" : "  ·  v" + r.version));
            h.tag.setText(r.system ? "SISTEMA" : "");
            Drawable d = icons.get(r.pkg);
            if (d == null) {
                try {
                    d = getPackageManager().getApplicationIcon(r.pkg);
                } catch (Exception e) {
                    d = getResources().getDrawable(android.R.drawable.sym_def_app_icon);
                }
                icons.put(r.pkg, d);
            }
            h.icon.setImageDrawable(d);
            return convert;
        }
    }

    private static final class Holder {
        ImageView icon;
        TextView name, info, tag;
    }
}
