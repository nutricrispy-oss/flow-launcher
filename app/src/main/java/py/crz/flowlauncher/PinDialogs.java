package py.crz.flowlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;

/** Diálogos para pedir, crear y verificar el PIN. */
final class PinDialogs {
    private PinDialogs() {}

    interface PinCallback {
        void onPin(String pin);
    }

    /** Ejecuta la acción solo si el PIN es correcto (o si no hay PIN configurado). */
    static void require(Activity a, Prefs p, String title, Runnable ok) {
        if (!p.hasPin()) {
            ok.run();
            return;
        }
        ask(a, title == null ? "Ingresá el PIN" : title, pin -> {
            if (p.checkPin(pin)) ok.run();
            else Ui.toast(a, "PIN incorrecto");
        });
    }

    static void ask(Activity a, String title, PinCallback cb) {
        final EditText et = new EditText(a);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et.setGravity(Gravity.CENTER);
        et.setTextSize(28);
        et.setSingleLine(true);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        FrameLayout wrap = new FrameLayout(a);
        int pad = Ui.dp(a, 24);
        wrap.setPadding(pad, Ui.dp(a, 8), pad, 0);
        wrap.addView(et);

        final AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("Aceptar", (di, w) -> cb.onPin(et.getText().toString().trim()))
                .setNegativeButton("Cancelar", null)
                .create();
        et.setOnEditorActionListener((v, id, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (id == EditorInfo.IME_ACTION_DONE || enter) {
                d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        d.show();
        et.requestFocus();
    }

    static void create(Activity a, Prefs p, Runnable done) {
        ask(a, "Nuevo PIN (4 a 8 números)", pin1 -> {
            if (pin1.length() < 4) {
                Ui.toast(a, "El PIN debe tener al menos 4 números");
                return;
            }
            ask(a, "Repetí el nuevo PIN", pin2 -> {
                if (!pin1.equals(pin2)) {
                    Ui.toast(a, "Los PIN no coinciden. Intentá de nuevo.");
                    return;
                }
                p.setPin(pin1);
                Ui.toast(a, "PIN guardado");
                if (done != null) done.run();
            });
        });
    }
}
