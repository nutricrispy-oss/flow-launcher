package py.crz.flowlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Guarda y carga el fondo de pantalla personalizado. */
final class Wallpaper {
    private Wallpaper() {}

    /** Imágenes incluidas en la app (res/drawable-nodpi/wp_N.jpg). Las 6 primeras van con cada tema. */
    static final String[] BUILTIN_NAMES = {"Ondas azules", "Cielo estrellado", "Aurora", "Nebulosa",
            "Atardecer", "Nubes suaves", "Bokeh dorado", "Océano"};

    static int builtinRes(Context c, int index) {
        return c.getResources().getIdentifier("wp_" + index, "drawable", c.getPackageName());
    }

    /** Carga una imagen incluida reduciéndola hasta maxWidth. */
    static Bitmap loadBuiltin(Context c, int index, int maxWidth) {
        int res = builtinRes(c, index);
        if (res == 0) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(c.getResources(), res, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxWidth) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeResource(c.getResources(), res, o2);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Qué imagen corresponde según el modo: índice incluido (>=0), -1 = sin imagen, -2 = personalizada. */
    static int resolve(Context c, Prefs p) {
        String m = p.wallpaperMode();
        if (Prefs.WP_CUSTOM.equals(m)) return file(c).exists() ? -2 : p.theme();
        if (Prefs.WP_GRADIENT.equals(m)) return -1;
        if (Prefs.WP_BUILTIN.equals(m)) return p.wallpaperBuiltin();
        return p.theme(); // auto: la imagen del tema
    }

    static File file(Context c) {
        return new File(c.getFilesDir(), "wallpaper.jpg");
    }

    /** Copia una imagen elegida por el usuario, reducida a tamaño de TV (máx. ~1920 px). */
    static boolean saveFromUri(Context c, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 1920 && o.outHeight / (sample * 2) >= 1080) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, o2);
            }
            if (b == null) return false;
            if (b.getWidth() > 2560) {
                int h = Math.round(b.getHeight() * (2560f / b.getWidth()));
                Bitmap s = Bitmap.createScaledBitmap(b, 2560, h, true);
                if (s != b) b.recycle();
                b = s;
            }
            File tmp = new File(c.getFilesDir(), "wallpaper.tmp");
            try (FileOutputStream fo = new FileOutputStream(tmp)) {
                b.compress(Bitmap.CompressFormat.JPEG, 88, fo);
            }
            b.recycle();
            return tmp.renameTo(file(c));
        } catch (Throwable e) {
            return false;
        }
    }

    static boolean saveBytes(Context c, byte[] data) {
        try (FileOutputStream fo = new FileOutputStream(file(c))) {
            fo.write(data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static byte[] readBytes(Context c) {
        File f = file(c);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    static Bitmap load(Context c, int maxWidth) {
        File f = file(c);
        if (!f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= maxWidth) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Throwable e) {
            return null;
        }
    }
}
