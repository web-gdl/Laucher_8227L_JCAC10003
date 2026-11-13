package com.android.launcher3.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Puntos de página: el actual es una píldora acentuada. */
public class PageDots extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private int count = 6, index;

    public PageDots(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
    }

    public void set(int count, int index) {
        if (count == this.count && index == this.index) return;
        this.count = count; this.index = index;
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize((int) (count * 18 * d + 26 * d), w), resolveSize((int) (16 * d), h));
    }

    @Override protected void onDraw(Canvas c) {
        float dot = 10 * d, wide = 26 * d, gap = 8 * d;
        float total = (count - 1) * (dot + gap) + wide;
        float x = (getWidth() - total) / 2f, cy = getHeight() / 2f;
        for (int i = 0; i < count; i++) {
            float w = i == index ? wide : dot;
            paint.setColor(i == index ? 0xFFFFB347 : 0x4DFFFFFF);
            r.set(x, cy - dot / 2, x + w, cy + dot / 2);
            c.drawRoundRect(r, dot / 2, dot / 2, paint);
            x += w + gap;
        }
    }
}
