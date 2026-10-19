package com.android.launcher3.ui;

/**
 * Fondo de pantalla completa hecho con los colores de la portada, muy difuminado.
 *
 * No se usa la imagen: se reduce la portada a una rejilla de {@link #GRID}x{@link #GRID} colores medios
 * (la «paleta»), y con esa rejilla se pinta un bitmap diminuto de {@link #OUT_W}x{@link #OUT_H} con
 * interpolación bilineal, varias pasadas de desenfoque de caja, un poco más de saturación y oscurecido
 * para que el texto se lea. El ImageView lo amplía a toda la pantalla con filtro.
 *
 * Coste: unas decenas de miles de operaciones con enteros por portada (no por fotograma) y un bitmap
 * de ~9 KB. Todo es Java puro sobre int[] para poder probarlo fuera de Android.
 */
public final class Backdrop {
    /** Lado de la rejilla de colores sacada de la portada. */
    public static final int GRID = 4;
    /** Bitmap de salida (proporción ≈ 1024x534, el área del launcher). */
    public static final int OUT_W = 64;
    public static final int OUT_H = 34;
    private static final int BLUR_RADIUS = 4;
    private static final int BLUR_PASSES = 3;
    /** Brillo final (0..1): fondo oscuro para que el texto claro se lea. */
    private static final float DARKEN = 0.50f;
    private static final float SATURATION = 1.20f;

    private Backdrop() {}

    /** Media de color por celda de una rejilla gw x gh sobre píxeles ARGB (w x h). */
    public static int[] averageGrid(int[] px, int w, int h, int gw, int gh) {
        int[] out = new int[gw * gh];
        for (int gy = 0; gy < gh; gy++) {
            int y0 = gy * h / gh, y1 = Math.max(y0 + 1, (gy + 1) * h / gh);
            for (int gx = 0; gx < gw; gx++) {
                int x0 = gx * w / gw, x1 = Math.max(x0 + 1, (gx + 1) * w / gw);
                long r = 0, g = 0, b = 0, n = 0;
                for (int y = y0; y < y1 && y < h; y++) {
                    for (int x = x0; x < x1 && x < w; x++) {
                        int c = px[y * w + x];
                        r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF; n++;
                    }
                }
                if (n == 0) n = 1;
                out[gy * gw + gx] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        return out;
    }

    /** Pinta el fondo OUT_W x OUT_H a partir de la rejilla GRID x GRID. */
    public static int[] render(int[] grid) {
        int n = OUT_W * OUT_H;
        float[] r = new float[n], g = new float[n], b = new float[n];
        // 1) Interpolación bilineal de la rejilla a todo el lienzo
        for (int y = 0; y < OUT_H; y++) {
            float fy = (y + 0.5f) * GRID / OUT_H - 0.5f;
            int y0 = clamp((int) Math.floor(fy), 0, GRID - 1), y1 = clamp(y0 + 1, 0, GRID - 1);
            float ty = clampf(fy - y0, 0f, 1f);
            for (int x = 0; x < OUT_W; x++) {
                float fx = (x + 0.5f) * GRID / OUT_W - 0.5f;
                int x0 = clamp((int) Math.floor(fx), 0, GRID - 1), x1 = clamp(x0 + 1, 0, GRID - 1);
                float tx = clampf(fx - x0, 0f, 1f);
                int c00 = grid[y0 * GRID + x0], c10 = grid[y0 * GRID + x1], c01 = grid[y1 * GRID + x0], c11 = grid[y1 * GRID + x1];
                int i = y * OUT_W + x;
                r[i] = lerp2(ch(c00, 16), ch(c10, 16), ch(c01, 16), ch(c11, 16), tx, ty);
                g[i] = lerp2(ch(c00, 8), ch(c10, 8), ch(c01, 8), ch(c11, 8), tx, ty);
                b[i] = lerp2(ch(c00, 0), ch(c10, 0), ch(c01, 0), ch(c11, 0), tx, ty);
            }
        }
        // 2) Desenfoque de caja (varias pasadas ≈ gaussiano)
        for (int p = 0; p < BLUR_PASSES; p++) {
            boxBlur(r); boxBlur(g); boxBlur(b);
        }
        // 3) Saturación y oscurecido
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            float l = 0.299f * r[i] + 0.587f * g[i] + 0.114f * b[i];
            float rr = (l + (r[i] - l) * SATURATION) * DARKEN;
            float gg = (l + (g[i] - l) * SATURATION) * DARKEN;
            float bb = (l + (b[i] - l) * SATURATION) * DARKEN;
            out[i] = 0xFF000000 | to8(rr) << 16 | to8(gg) << 8 | to8(bb);
        }
        return out;
    }

    /** Paleta → texto para guardarla en preferencias ("rrggbb,rrggbb,..."); null si no es válida. */
    public static String encode(int[] grid) {
        if (grid == null || grid.length != GRID * GRID) return null;
        StringBuilder sb = new StringBuilder(GRID * GRID * 7);
        for (int i = 0; i < grid.length; i++) {
            if (i > 0) sb.append(',');
            String h = Integer.toHexString(grid[i] & 0xFFFFFF);
            for (int k = h.length(); k < 6; k++) sb.append('0');
            sb.append(h);
        }
        return sb.toString();
    }

    public static int[] decode(String s) {
        if (s == null) return null;
        String[] parts = s.split(",");
        if (parts.length != GRID * GRID) return null;
        int[] grid = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) grid[i] = 0xFF000000 | Integer.parseInt(parts[i].trim(), 16);
        } catch (NumberFormatException e) {
            return null;
        }
        return grid;
    }

    // ------------------------------------------------------------------ utilidades

    private static void boxBlur(float[] c) {
        float[] tmp = new float[c.length];
        int rad = BLUR_RADIUS;
        for (int y = 0; y < OUT_H; y++) {                       // horizontal
            for (int x = 0; x < OUT_W; x++) {
                float s = 0; int k = 0;
                for (int d = -rad; d <= rad; d++) { int xx = clamp(x + d, 0, OUT_W - 1); s += c[y * OUT_W + xx]; k++; }
                tmp[y * OUT_W + x] = s / k;
            }
        }
        for (int x = 0; x < OUT_W; x++) {                       // vertical
            for (int y = 0; y < OUT_H; y++) {
                float s = 0; int k = 0;
                for (int d = -rad; d <= rad; d++) { int yy = clamp(y + d, 0, OUT_H - 1); s += tmp[yy * OUT_W + x]; k++; }
                c[y * OUT_W + x] = s / k;
            }
        }
    }

    private static float ch(int c, int shift) { return (c >> shift) & 0xFF; }

    private static float lerp2(float a, float b, float c, float d, float tx, float ty) {
        float top = a + (b - a) * tx, bot = c + (d - c) * tx;
        return top + (bot - top) * ty;
    }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private static float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private static int to8(float v) { int i = Math.round(v); return i < 0 ? 0 : (i > 255 ? 255 : i); }
}
