package py.crz.flowlauncher;

import android.graphics.drawable.Drawable;

/** Una aplicación instalada que se puede abrir. */
final class AppItem {
    String pkg;
    String activity;
    String label;
    Drawable icon;
    Drawable banner;
    boolean system;
    boolean leanback;
}
