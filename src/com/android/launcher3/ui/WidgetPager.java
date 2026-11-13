package com.android.launcher3.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Páginas que se cambian deslizando (swipe o flick). Sin AndroidX: solo la página actual (y la vecina mientras se arrastra)
 * está visible. Las páginas hijas pueden tener sus propios toques; el deslizamiento horizontal se intercepta al pasar el umbral.
 * Un hijo que pida no ser interceptado (p. ej. el arco del velocímetro, que arrastra el límite) gana el gesto.
 */
public class WidgetPager extends FrameLayout {
    public interface Listener { void onPage(int index); }

    private final int slop, minFling;
    private int page;
    private float downX, downY, lastX;
    private boolean dragging;
    private VelocityTracker velocity;
    private Listener listener;

    public WidgetPager(Context c, AttributeSet a) {
        super(c, a);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 4;
    }

    public void setListener(Listener l) { listener = l; }

    public int page() { return page; }

    public int count() { return getChildCount(); }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        showOnly(page);
    }

    private void showOnly(int idx) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            v.setTranslationX(0);
            v.setVisibility(i == idx ? VISIBLE : GONE);
        }
    }

    /** Va a una página; con animate = true la desliza. */
    public void setPage(int idx, boolean animate) {
        idx = Math.max(0, Math.min(getChildCount() - 1, idx));
        if (idx == page) return;
        int from = page;
        page = idx;
        if (!animate || getWidth() == 0) showOnly(idx);
        else slide(from, idx, 0f);
        if (listener != null) listener.onPage(idx);
    }

    private void slide(int from, int to, float startOffset) {
        final View out = getChildAt(from), in = getChildAt(to);
        float w = getWidth();
        float dir = to > from ? -1f : 1f;
        in.setVisibility(VISIBLE);
        in.setTranslationX(-dir * w + startOffset);
        out.setTranslationX(startOffset);
        out.animate().translationX(dir * w).setDuration(220).withEndAction(() -> {
            if (page == to) showOnly(to);
        }).start();
        in.animate().translationX(0).setDuration(220).start();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX(); downY = e.getY(); dragging = false;
                abortAnimations();
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    startDrag(e);
                    return true;
                }
                break;
            default:
        }
        return false;
    }

    private void abortAnimations() {
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).animate().cancel();
        showOnly(page);
    }

    private void startDrag(MotionEvent e) {
        dragging = true;
        lastX = e.getX();
        velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = lastX = e.getX(); downY = e.getY();
            return true; // sin hijos que traten el toque, seguimos para poder deslizar
        }
        if (!dragging) {
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE && Math.abs(e.getX() - downX) > slop
                    && Math.abs(e.getX() - downX) > Math.abs(e.getY() - downY) * 1.2f) startDrag(e);
            return true;
        }
        if (velocity != null) velocity.addMovement(e);
        float w = Math.max(1, getWidth());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                float off = e.getX() - downX;
                // sin página vecina en ese lado: resistencia
                boolean none = (off > 0 && page == 0) || (off < 0 && page == getChildCount() - 1);
                if (none) off *= 0.25f;
                getChildAt(page).setTranslationX(off);
                int nb = off < 0 ? page + 1 : page - 1;
                if (!none && nb >= 0 && nb < getChildCount()) {
                    View n = getChildAt(nb);
                    n.setVisibility(VISIBLE);
                    n.setTranslationX(off + (off < 0 ? w : -w));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float off = e.getX() - downX;
                float vx = 0;
                if (velocity != null) { velocity.computeCurrentVelocity(1000); vx = velocity.getXVelocity(); }
                int target = page;
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (off < -w / 4 || (vx < -minFling && off < -slop)) target = page + 1;
                    else if (off > w / 4 || (vx > minFling && off > slop)) target = page - 1;
                }
                target = Math.max(0, Math.min(getChildCount() - 1, target));
                int from = page;
                dragging = false;
                if (velocity != null) { velocity.recycle(); velocity = null; }
                if (target != from) {
                    page = target;
                    slide(from, target, off);
                    if (listener != null) listener.onPage(target);
                } else {
                    // volver a su sitio y ocultar la vecina
                    View cur = getChildAt(page);
                    cur.animate().translationX(0).setDuration(150).start();
                    for (int i = 0; i < getChildCount(); i++) {
                        if (i == page) continue;
                        final View v = getChildAt(i);
                        if (v.getVisibility() == VISIBLE) {
                            float t = i > page ? w : -w;
                            v.animate().translationX(t).setDuration(150).withEndAction(() -> { v.setVisibility(GONE); v.setTranslationX(0); }).start();
                        }
                    }
                }
                return true;
            }
            default:
                return true;
        }
    }
}
