package com.android.launcher3.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.R;

/**
 * Velocímetro de arco (270°, 0–180 km/h) con degradado verde → amarillo → rojo y marca del límite de velocidad.
 * Por encima del límite el número se pone rojo. Sin asignaciones al dibujar.
 */
public final class SpeedGaugeView extends View {

    private static final float MAX_KMH = 180f;
    private static final float START = 135f, SWEEP = 270f; // de abajo-izquierda a abajo-derecha, por arriba

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint limitMark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint limitText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int GREEN = 0xFF43D17A, YELLOW = 0xFFFFD54F, RED = 0xFFFF5252;
    private final int inkColor;
    private int limitKmh;
    private String limitLabel = "";
    private final RectF arc = new RectF();
    private float kmh = -1f;
    private String label = "--";

    public SpeedGaugeView(Context c, AttributeSet a) {
        super(c, a);
        int ink = c.getColor(R.color.ink), dim = c.getColor(R.color.text_dim);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0x1FFFFFFF);
        progress.setStyle(Paint.Style.STROKE);
        progress.setStrokeCap(Paint.Cap.ROUND);
        tick.setStyle(Paint.Style.STROKE);
        tick.setColor(0x59FFFFFF);
        inkColor = ink;
        number.setColor(ink);
        number.setTextAlign(Paint.Align.CENTER);
        number.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        number.setFontFeatureSettings("tnum");
        unit.setColor(dim);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setLetterSpacing(0.1f);
        limitMark.setStyle(Paint.Style.STROKE);
        limitMark.setStrokeCap(Paint.Cap.ROUND);
        limitMark.setColor(RED);
        limitText.setColor(RED);
        limitText.setTextAlign(Paint.Align.CENTER);
        limitText.setFontFeatureSettings("tnum");
    }

    /** km/h; negativo = sin dato («--»). */
    public void setSpeed(float value) {
        int shown = value < 0 ? -1 : Math.round(value);
        int before = kmh < 0 ? -1 : Math.round(kmh);
        kmh = value;
        if (shown == before) { updateNumberColor(); return; }
        label = shown < 0 ? "--" : String.valueOf(shown);
        setContentDescription(shown < 0 ? null : shown + " km/h");
        updateNumberColor();
        invalidate();
    }

    /** Límite en km/h; 0 = sin límite. */
    public void setLimit(int value) {
        if (value == limitKmh) return;
        limitKmh = Math.max(0, value);
        limitLabel = limitKmh > 0 ? String.valueOf(limitKmh) : "";
        updateNumberColor();
        invalidate();
    }

    private void updateNumberColor() {
        int col = limitKmh > 0 && kmh > limitKmh ? RED : inkColor;
        if (number.getColor() != col) { number.setColor(col); invalidate(); }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float size = Math.min(w, h), stroke = size * 0.078f;
        track.setStrokeWidth(stroke);
        progress.setStrokeWidth(stroke);
        tick.setStrokeWidth(Math.max(1f, size * 0.011f));
        float inset = stroke / 2f + 1f;
        float l = (w - size) / 2f, t = (h - size) / 2f;
        arc.set(l + inset, t + inset, l + size - inset, t + size - inset);
        // Degradado a lo largo del arco. Empieza 10° antes para que la punta redondeada del inicio quede verde.
        float cx = arc.centerX(), cy = arc.centerY(), lead = 10f / 360f;
        SweepGradient g = new SweepGradient(cx, cy, new int[]{GREEN, GREEN, YELLOW, RED, RED},
                new float[]{0f, lead, lead + SWEEP / 2f / 360f, lead + SWEEP / 360f, 1f});
        Matrix m = new Matrix();
        m.setRotate(START - 10f, cx, cy);
        g.setLocalMatrix(m);
        progress.setShader(g);
        limitMark.setStrokeWidth(Math.max(2f, size * 0.022f));
        limitText.setTextSize(size * 0.075f);
        number.setTextSize(size * 0.30f);
        unit.setTextSize(size * 0.085f);
    }

    @Override protected void onDraw(Canvas c) {
        float cx = arc.centerX(), cy = arc.centerY(), r = arc.width() / 2f;
        c.drawArc(arc, START, SWEEP, false, track);
        if (kmh > 0) c.drawArc(arc, START, SWEEP * Math.min(1f, kmh / MAX_KMH), false, progress);
        // Marcas cada 20 km/h (más largas cada 40), por dentro del arco
        float inner = r - track.getStrokeWidth() / 2f - r * 0.06f;
        for (int v = 0; v <= MAX_KMH; v += 20) {
            double a = Math.toRadians(START + SWEEP * v / MAX_KMH);
            float len = r * (v % 40 == 0 ? 0.11f : 0.06f);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + cos * inner, cy + sin * inner, cx + cos * (inner - len), cy + sin * (inner - len), tick);
        }
        if (limitKmh > 0 && limitKmh <= MAX_KMH) {
            // Marca del límite cruzando el arco y su número por dentro
            double a = Math.toRadians(START + SWEEP * limitKmh / MAX_KMH);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a), half = track.getStrokeWidth() * 0.75f;
            c.drawLine(cx + cos * (r - half), cy + sin * (r - half), cx + cos * (r + half), cy + sin * (r + half), limitMark);
            float tr = inner - r * 0.25f;
            c.drawText(limitLabel, cx + cos * tr, cy + sin * tr + limitText.getTextSize() * 0.36f, limitText);
        }
        float base = cy + number.getTextSize() * 0.36f;
        c.drawText(label, cx, base, number);
        c.drawText("km/h", cx, base + unit.getTextSize() * 1.9f, unit);
    }
}
