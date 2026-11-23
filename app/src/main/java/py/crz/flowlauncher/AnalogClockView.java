package py.crz.flowlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Calendar;

/** Reloj analógico dibujado (para el estilo "Analógico"). */
final class AnalogClockView extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sec = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);

    AnalogClockView(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        hand.setStrokeCap(Paint.Cap.ROUND);
        sec.setStrokeCap(Paint.Cap.ROUND);
        tick.setStrokeCap(Paint.Cap.ROUND);
    }

    void setColors(int main, int accent) {
        ring.setColor((main & 0x00FFFFFF) | 0x66000000);
        hand.setColor(main);
        tick.setColor(main);
        sec.setColor(accent);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - 4;
        float u = r / 40f;
        ring.setStrokeWidth(2.5f * u);
        c.drawCircle(cx, cy, r, ring);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            float in = i % 3 == 0 ? r * 0.78f : r * 0.86f;
            tick.setStrokeWidth((i % 3 == 0 ? 3f : 1.8f) * u);
            c.drawLine(cx + (float) Math.sin(a) * in, cy - (float) Math.cos(a) * in,
                    cx + (float) Math.sin(a) * r * 0.94f, cy - (float) Math.cos(a) * r * 0.94f, tick);
        }
        Calendar cal = Calendar.getInstance();
        float s = cal.get(Calendar.SECOND);
        float m = cal.get(Calendar.MINUTE) + s / 60f;
        float hr = cal.get(Calendar.HOUR) + m / 60f;
        hand.setStrokeWidth(4.5f * u);
        drawHand(c, cx, cy, hr / 12f, r * 0.5f, hand);
        hand.setStrokeWidth(3f * u);
        drawHand(c, cx, cy, m / 60f, r * 0.75f, hand);
        sec.setStrokeWidth(1.6f * u);
        drawHand(c, cx, cy, s / 60f, r * 0.82f, sec);
        c.drawCircle(cx, cy, 3.5f * u, sec);
    }

    private static void drawHand(Canvas c, float cx, float cy, float turn, float len, Paint p) {
        double a = Math.PI * 2 * turn;
        c.drawLine(cx, cy, cx + (float) Math.sin(a) * len, cy - (float) Math.cos(a) * len, p);
    }
}
