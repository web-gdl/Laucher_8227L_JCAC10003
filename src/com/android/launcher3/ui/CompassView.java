package com.android.launcher3.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.R;

/**
 * Brújula: la rosa gira con el rumbo del auto (rumbo arriba) y la flecha fija apunta hacia donde se avanza.
 * Sin rumbo conocido la rosa queda con el norte arriba y la flecha atenuada.
 */
public final class CompassView extends View {

    private static final String[] LETTERS = {"N", "E", "S", "O"};

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint north = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();
    private float heading = -1f;
    private final int arrowOn, arrowOff;

    public CompassView(Context c, AttributeSet a) {
        super(c, a);
        int accent = c.getColor(R.color.accent), dim = c.getColor(R.color.text_dim);
        arrowOn = c.getColor(R.color.ink);
        arrowOff = 0x40FFFFFF;
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0x26FFFFFF);
        tick.setStyle(Paint.Style.STROKE);
        tick.setColor(0x4DFFFFFF);
        letter.setColor(dim);
        letter.setTextAlign(Paint.Align.CENTER);
        letter.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        north.set(letter);
        north.setColor(accent);
        arrow.setStyle(Paint.Style.FILL);
        arrow.setColor(arrowOff);
    }

    /** Grados (0 = norte); negativo = desconocido. */
    public void setHeading(float deg) {
        float d = deg < 0 ? -1f : Math.round(deg);
        if (d == heading) return;
        heading = d;
        arrow.setColor(d < 0 ? arrowOff : arrowOn);
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float size = Math.min(w, h);
        ring.setStrokeWidth(Math.max(1f, size * 0.015f));
        tick.setStrokeWidth(Math.max(1f, size * 0.012f));
        letter.setTextSize(size * 0.14f);
        north.setTextSize(size * 0.15f);
        float cx = w / 2f, cy = h / 2f, r = size / 2f;
        arrowPath.reset();
        arrowPath.moveTo(cx, cy - r * 0.42f);
        arrowPath.lineTo(cx + r * 0.20f, cy + r * 0.26f);
        arrowPath.lineTo(cx, cy + r * 0.14f);
        arrowPath.lineTo(cx - r * 0.20f, cy + r * 0.26f);
        arrowPath.close();
    }

    @Override protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) / 2f - ring.getStrokeWidth();
        c.drawCircle(cx, cy, r, ring);
        c.save();
        c.rotate(heading < 0 ? 0f : -heading, cx, cy);
        for (int d = 0; d < 360; d += 15) {
            if (d % 90 == 0) continue; // ahí van las letras
            float len = r * (d % 45 == 0 ? 0.14f : 0.08f);
            double a = Math.toRadians(d - 90);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + cos * r, cy + sin * r, cx + cos * (r - len), cy + sin * (r - len), tick);
        }
        c.restore();
        // Letras derechas (legibles) en la posición que les toca con la rosa girada
        float lr = r * 0.74f, rot = heading < 0 ? 0f : heading;
        for (int i = 0; i < 4; i++) {
            Paint p = i == 0 ? north : letter;
            double a = Math.toRadians(i * 90f - rot);
            float x = cx + (float) Math.sin(a) * lr, y = cy - (float) Math.cos(a) * lr;
            c.drawText(LETTERS[i], x, y + p.getTextSize() * 0.36f, p);
        }
        c.drawPath(arrowPath, arrow);
    }
}
