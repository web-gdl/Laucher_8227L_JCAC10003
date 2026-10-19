package com.android.launcher3.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.launcher3.Diag;
import com.android.launcher3.Prefs;
import com.android.launcher3.R;
import com.android.launcher3.jancar.MediaState;

/**
 * Pinta el estado multimedia del inicio nocturno: portada redonda, título, artista, progreso y
 * tiempos, y el fondo de pantalla completa con los colores de la portada ({@link Backdrop}).
 * Sin temporizadores propios: solo redibuja cuando le avisan.
 *
 * El fondo se recalcula una vez por portada nueva, con un fundido de {@link #FADE_MS} ms. Si la
 * canción no trae portada se conserva el último fondo; la paleta se guarda en preferencias para
 * que tras reiniciar el estéreo aparezca el mismo fondo.
 */
public final class MediaWidget {
    private static final int FADE_MS = 600;
    /** Lado máximo de la copia reducida de la portada antes de sacar la rejilla de colores. */
    private static final int SAMPLE_MAX = 64;

    private final Context ctx;
    private final ImageView cover;
    private final ImageView backdrop;
    private final Prefs prefs;
    private final TextView source;
    private final TextView title;
    private final TextView artist;
    private final ProgressBar progress;
    private final View times;
    private final TextView pos;
    private final TextView dur;
    private final ImageButton play;
    private final int dimColor;
    private final int coverPad;
    private final StringBuilder sb = new StringBuilder(16);
    private Bitmap shownCover;
    private Drawable backdropDrawable;
    private String backdropPalette;
    private boolean lastPlaying;
    private boolean lastOnline = true;

    public MediaWidget(View root, ImageView backdrop, Prefs prefs) {
        ctx = root.getContext();
        this.backdrop = backdrop;
        this.prefs = prefs;
        cover = root.findViewById(R.id.media_cover);
        source = root.findViewById(R.id.media_source);
        title = root.findViewById(R.id.media_title);
        artist = root.findViewById(R.id.media_artist);
        progress = root.findViewById(R.id.media_progress);
        times = root.findViewById(R.id.media_times);
        pos = root.findViewById(R.id.media_pos);
        dur = root.findViewById(R.id.media_dur);
        play = root.findViewById(R.id.media_play);
        dimColor = ctx.getColor(R.color.text_dim);
        coverPad = ctx.getResources().getDimensionPixelSize(R.dimen.cover_size) / 3;
        cover.setClipToOutline(true); // recorta la portada al óvalo del fondo
        title.setSelected(true);       // marquee
        setCover(null);
        int[] saved = Backdrop.decode(prefs.getBackdrop());
        if (saved != null) applyBackdrop(saved, Backdrop.encode(saved), false);
    }

    public void render(MediaState s, boolean online) {
        if (!online) {
            if (lastOnline) {
                source.setText(R.string.media_service_offline);
                title.setText(R.string.media_idle);
                artist.setText("");
                setCover(null);
                showProgress(false);
                setPlaying(false);
                lastOnline = false;
            }
            return;
        }
        lastOnline = true;

        if (s.hasContent()) {
            source.setText(ctx.getString(R.string.media_now_playing) + " · " + ctx.getString(sourceLabel(s.type)));
            title.setText(s.title);
            artist.setText(s.artist == null ? "" : s.artist);
        } else {
            source.setText(sourceLabel(s.type));
            title.setText(R.string.media_idle);
            artist.setText(R.string.media_open_hint);
        }
        setCover(s.cover);
        setPlaying(s.isPlaying());

        if (s.hasContent() && s.duration > 0 && s.type != MediaState.TYPE_RADIO) {
            showProgress(true);
            int p = Math.max(0, Math.min(s.position, s.duration));
            progress.setProgress((int) (1000L * p / s.duration));
            sb.setLength(0); pos.setText(appendTime(sb, p));
            sb.setLength(0); dur.setText(appendTime(sb, s.duration));
        } else {
            showProgress(false);
        }
    }

    /**
     * Sin fuente activa: muestra la última canción conocida (atenuada, en pausa) para tocar y seguir.
     * El fondo de colores se conserva.
     */
    public void renderLast(String lastTitle, String lastArtist) {
        lastOnline = true;
        source.setText(R.string.media_last);
        title.setText(lastTitle);
        artist.setText(lastArtist == null ? "" : lastArtist);
        setCover(null);
        setPlaying(false);
        showProgress(false);
    }

    /** Suelta el fondo (bitmap propio de ~9 KB). La portada es del puente y la recicla él. */
    public void release() {
        backdrop.setImageDrawable(null);
        backdropDrawable = null;
    }

    private void showProgress(boolean show) {
        int v = show ? View.VISIBLE : View.INVISIBLE;
        if (progress.getVisibility() != v) { progress.setVisibility(v); times.setVisibility(v); }
    }

    private void setCover(Bitmap b) {
        if (b == shownCover && b != null) return;
        shownCover = b;
        if (b == null || b.isRecycled()) {
            cover.setImageResource(R.drawable.ic_music_note);
            cover.setImageTintList(ColorStateList.valueOf(dimColor));
            cover.setPadding(coverPad, coverPad, coverPad, coverPad);
            // Sin portada: se conserva el último fondo hasta que llegue una nueva.
        } else {
            cover.setImageTintList(null);
            cover.setPadding(0, 0, 0, 0);
            cover.setImageBitmap(b);
            updateBackdrop(b);
        }
    }

    /** Saca la rejilla de colores de la portada y, si cambió, pinta el fondo nuevo con fundido. */
    private void updateBackdrop(Bitmap b) {
        try {
            long t0 = System.nanoTime();
            Bitmap s = b;
            while (s.getWidth() > SAMPLE_MAX || s.getHeight() > SAMPLE_MAX) { // reducciones a la mitad ≈ media de 2x2
                Bitmap n = Bitmap.createScaledBitmap(s, Math.max(1, s.getWidth() / 2), Math.max(1, s.getHeight() / 2), true);
                if (s != b) s.recycle();
                s = n;
            }
            int w = s.getWidth(), h = s.getHeight();
            int[] px = new int[w * h];
            s.getPixels(px, 0, w, 0, 0, w, h);
            if (s != b) s.recycle();
            int[] grid = Backdrop.averageGrid(px, w, h, Backdrop.GRID, Backdrop.GRID);
            String palette = Backdrop.encode(grid);
            if (palette.equals(backdropPalette)) return;
            applyBackdrop(grid, palette, true);
            prefs.setBackdrop(palette);
            Diag.log("media", "fondo nuevo en " + (System.nanoTime() - t0) / 1000000 + " ms");
        } catch (RuntimeException | OutOfMemoryError e) {
            Diag.warn("media", "fondo", e);
        }
    }

    private void applyBackdrop(int[] grid, String palette, boolean fade) {
        Bitmap bmp = Bitmap.createBitmap(Backdrop.render(grid), Backdrop.OUT_W, Backdrop.OUT_H, Bitmap.Config.ARGB_8888);
        BitmapDrawable d = new BitmapDrawable(ctx.getResources(), bmp);
        d.setFilterBitmap(true);
        if (fade && backdropDrawable != null) {
            TransitionDrawable t = new TransitionDrawable(new Drawable[]{backdropDrawable, d});
            t.setCrossFadeEnabled(true);
            backdrop.setImageDrawable(t);
            t.startTransition(FADE_MS);
        } else {
            backdrop.setImageDrawable(d);
        }
        backdropDrawable = d;
        backdropPalette = palette;
    }

    private void setPlaying(boolean playing) {
        if (playing == lastPlaying && play.getDrawable() != null) return;
        lastPlaying = playing;
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(ctx.getString(playing ? R.string.pause : R.string.play));
    }

    private static int sourceLabel(int type) {
        switch (type) {
            case MediaState.TYPE_MUSIC: return R.string.media_source_music;
            case MediaState.TYPE_RADIO: return R.string.media_source_radio;
            case MediaState.TYPE_VIDEO: return R.string.media_source_video;
            case MediaState.TYPE_A2DP: return R.string.media_source_bt;
            case MediaState.TYPE_NONE: return R.string.media_source_music;
            default: return R.string.media_source_other;
        }
    }

    private static StringBuilder appendTime(StringBuilder sb, int seconds) {
        if (seconds < 0) seconds = 0;
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) sb.append(h).append(':');
        if (h > 0 && m < 10) sb.append('0');
        sb.append(m).append(':');
        if (s < 10) sb.append('0');
        sb.append(s);
        return sb;
    }
}
