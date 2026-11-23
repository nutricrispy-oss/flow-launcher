package py.crz.flowlauncher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Comparte el APK descargado con el instalador de Android (como hace Downloader). */
public class ApkProvider extends ContentProvider {
    static final String AUTHORITY = "py.crz.flowlauncher.apk";
    static final String MIME = "application/vnd.android.package-archive";

    static Uri uri() {
        return Uri.parse("content://" + AUTHORITY + "/FlowLauncher.apk");
    }

    static File file(android.content.Context c) {
        return new File(c.getCacheDir(), "update.apk");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(getContext());
        if (!f.exists()) throw new FileNotFoundException("No hay APK descargado");
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return MIME;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f = file(getContext());
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{"FlowLauncher.apk", f.length()});
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
