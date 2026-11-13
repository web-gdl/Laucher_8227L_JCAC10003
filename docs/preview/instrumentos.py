#!/usr/bin/env python3
"""Inicio 3.7 (diseño A, tres tarjetas): música, velocímetro con brújula y viaje; dock sin círculos.

Genera docs/screenshots/01, 02 y 05 (render del diseño, no capturas del equipo).
    pip install playwright pillow && python3 docs/preview/instrumentos.py
"""
import math, pathlib, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from nocturno import INK, ring, ring_app, aicon, APPS  # noqa: E402
from propuestas import frame, ico, cover, btn, prog, ACC, DIM, ART  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

SHOTS = pathlib.Path(__file__).resolve().parent.parent / "screenshots"
GLASS = "background:rgba(0,0,0,.28);border:1px solid rgba(255,255,255,.08);border-radius:24px"
GREEN, YELLOW, RED = (0x43, 0xD1, 0x7A), (0xFF, 0xD5, 0x4F), (0xFF, 0x52, 0x52)


def cardinal(deg):
    return ["N", "NE", "E", "SE", "S", "SO", "O", "NO"][int((deg + 22.5) % 360 // 45)]


def pt(c, r, a):
    return c + r * math.sin(math.radians(a)), c - r * math.cos(math.radians(a))


def mix(t):
    a, b, u = (GREEN, YELLOW, t / .5) if t < .5 else (YELLOW, RED, (t - .5) / .5)
    return "#%02X%02X%02X" % tuple(round(a[i] + (b[i] - a[i]) * u) for i in range(3))


def speed_gauge(speed, limit, size=250, maxv=180, stroke=14):
    c, r = size / 2, size / 2 - stroke / 2 - 1
    a0, sweep = -135, 270
    track = ""
    x0, y0 = pt(c, r, a0); x1, y1 = pt(c, r, a0 + sweep)
    track = f'<path d="M{x0:.1f},{y0:.1f} A{r},{r} 0 1 1 {x1:.1f},{y1:.1f}" stroke="rgba(255,255,255,.12)" stroke-width="{stroke}" fill="none" stroke-linecap="round"/>'
    prog_ = ""
    if speed and speed > 0:
        end = sweep * min(1, speed / maxv); n = max(2, int(end / 2))
        for i in range(n):  # degradado verde → amarillo → rojo a lo largo del arco
            s, e = a0 + end * i / n, a0 + end * (i + 1) / n + .6
            (sx, sy), (ex, ey) = pt(c, r, s), pt(c, r, min(e, a0 + end))
            cap = "round" if i in (0, n - 1) else "butt"
            prog_ += f'<path d="M{sx:.1f},{sy:.1f} A{r},{r} 0 0 1 {ex:.1f},{ey:.1f}" stroke="{mix((s - a0) / sweep)}" stroke-width="{stroke}" fill="none" stroke-linecap="{cap}"/>'
    ticks, inner = "", r - stroke / 2 - r * .06
    for v in range(0, maxv + 1, 20):
        a = a0 + sweep * v / maxv; l = r * (.11 if v % 40 == 0 else .06)
        (px, py), (qx, qy) = pt(c, inner, a), pt(c, inner - l, a)
        ticks += f'<line x1="{px:.1f}" y1="{py:.1f}" x2="{qx:.1f}" y2="{qy:.1f}" stroke="rgba(255,255,255,.35)" stroke-width="2"/>'
    a = a0 + sweep * (limit or 0) / maxv
    kx, ky = pt(c, r, a)
    op = 1 if limit else .43
    lim = (f'<circle cx="{kx:.1f}" cy="{ky:.1f}" r="{stroke*.8:.1f}" fill="#fff" fill-opacity="{op}" stroke="#FF5252" stroke-opacity="{op}" stroke-width="4"/>')
    lim_txt = (f"límite {limit}" if limit else "sin límite")
    lim_col = "#FF5252" if limit else DIM
    num_col = "#FF5252" if (speed and limit and speed > limit) else INK
    label = "--" if speed is None else str(speed)
    return (f'<div style="position:relative;width:{size}px;height:{size}px;flex:none">'
            f'<svg width="{size}" height="{size}">{track}{prog_}{ticks}{lim}</svg>'
            f'<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;padding-top:8px">'
            f'<div style="font-size:{int(size*.30)}px;font-weight:300;color:{num_col};line-height:1">{label}</div>'
            f'<div style="margin-top:6px;font-size:{int(size*.085)}px;color:{DIM};letter-spacing:1px">km/h</div>'
            f'<div style="margin-top:8px;font-size:{int(size*.075)}px;color:{lim_col}">{lim_txt}</div></div></div>')


def compass(heading, size=96):
    c, rr = size / 2, size / 2 - 2
    rot = heading if heading is not None else 0
    ticks = ""
    for d in range(0, 360, 15):
        if d % 90 == 0: continue
        l = rr * (.14 if d % 45 == 0 else .08)
        (px, py), (qx, qy) = pt(c, rr, d - rot), pt(c, rr - l, d - rot)
        ticks += f'<line x1="{px:.1f}" y1="{py:.1f}" x2="{qx:.1f}" y2="{qy:.1f}" stroke="rgba(255,255,255,.3)" stroke-width="1.5"/>'
    letters = ""
    for d, t in ((0, "N"), (90, "E"), (180, "S"), (270, "O")):
        x, y = pt(c, rr * .74, d - rot)
        letters += f'<text x="{x:.1f}" y="{y + 5.5:.1f}" text-anchor="middle" font-size="{13 if t == "N" else 12}" font-weight="600" fill="{ACC if t == "N" else DIM}" font-family="Inter">{t}</text>'
    arrow_col = INK if heading is not None else "rgba(255,255,255,.25)"
    arrow = f'<path d="M{c},{c - rr*.42:.1f} L{c + rr*.2:.1f},{c + rr*.26:.1f} L{c},{c + rr*.14:.1f} L{c - rr*.2:.1f},{c + rr*.26:.1f} Z" fill="{arrow_col}"/>'
    text = f'{cardinal(heading)} {heading}°' if heading is not None else "—"
    return (f'<div style="flex:1;display:flex;flex-direction:column;align-items:center">'
            f'<svg width="{size}" height="{size}"><circle cx="{c}" cy="{c}" r="{rr}" fill="none" stroke="rgba(255,255,255,.15)" stroke-width="1.5"/>{ticks}{letters}{arrow}</svg>'
            f'<div style="margin-top:6px;font-size:19px;color:{INK}">{text}</div></div>')


def odo(label, value, grow=1):
    return (f'<div style="flex:{grow}"><div style="font-size:12px;letter-spacing:2px;color:{DIM}">{label}</div>'
            f'<div style="font-size:21px;color:{INK};font-variant-numeric:tabular-nums">{value}</div></div>')


def dock_app(i):
    return (f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px;width:104px">{aicon(i, 62)}'
            f'<div style="font-size:13px;color:{DIM};text-align:center;line-height:1.15;height:30px;overflow:hidden">{APPS[i][0]}</div></div>')


def dock_all():
    return (f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px;width:104px"><div style="width:62px;height:62px;display:flex;align-items:center;justify-content:center">{ico("apps", 44, ACC)}</div>'
            f'<div style="font-size:13px;color:{DIM};text-align:center;line-height:1.15;height:30px;overflow:hidden">Todas</div></div>')


def svg(path, s=22, col=DIM):
    return f'<svg width="{s}" height="{s}" viewBox="0 0 24 24" fill="{col}"><path d="{path}"/></svg>'


CLOCK = "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zm0.5,-13H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z"
AVG = "M20.38,8.57l-1.23,1.85a8,8 0 0,1 -0.22,7.58H5.07A8,8 0 0,1 15.58,6.85l1.85,-1.23A10,10 0 0,0 3.35,19a2,2 0 0,0 1.72,1h13.85a2,2 0 0,0 1.74,-1 10,10 0 0,0 -0.27,-10.44zm-9.79,6.84a2,2 0 0,0 2.83,0l5.66,-8.49 -8.49,5.66a2,2 0 0,0 0,2.83z"
PEAK = "M14,6l-3.75,5 2.85,3.8 -1.6,1.2C9.81,13.75 7,10 7,10l-6,8h22L14,6z"
WRENCH = "M22.7,19l-9.1,-9.1c0.9,-2.3 0.4,-5 -1.5,-6.9 -2,-2 -5,-2.4 -7.4,-1.3L9,6 6,9 1.6,4.7C0.4,7.1 0.9,10.1 2.9,12.1c1.9,1.9 4.6,2.4 6.9,1.5l9.1,9.1c0.4,0.4 1,0.4 1.4,0l2.3,-2.3c0.5,-0.4 0.5,-1.1 0.1,-1.4z"


def stat(icon, label, val, unit=""):
    return (f'<div style="display:flex;align-items:center;gap:10px;padding:6px 0">{svg(icon, 22)}'
            f'<div style="flex:1;font-size:14px;color:{DIM}">{label}</div>'
            f'<div style="font-size:22px;color:{INK};font-variant-numeric:tabular-nums">{val}<span style="font-size:14px;color:{DIM};margin-left:4px">{unit}</span></div></div>')


def dock_app(i):
    return (f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px;width:104px">{aicon(i, 62)}'
            f'<div style="font-size:13px;color:{DIM};text-align:center;line-height:1.15;height:30px;overflow:hidden">{APPS[i][0]}</div></div>')


def dock_all():
    return (f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px;width:104px"><div style="width:62px;height:62px;display:flex;align-items:center;justify-content:center">{ico("apps", 44, ACC)}</div>'
            f'<div style="font-size:13px;color:{DIM};text-align:center;line-height:1.15;height:30px;overflow:hidden">Todas</div></div>')


def home(speed, heading, limit, trip, total, status=None, playing=True, stats=("0:42", "54", "118", "1 540"), service=("Servicio en <b style=\"color:%s;font-weight:600\">1 240 km</b>" % INK, 76)):
    bg = (f'<div style="position:absolute;inset:-80px;background:{ART};filter:blur(70px) saturate(1.2) brightness(.5)"></div>' if playing else "")
    bg += '<div style="position:absolute;left:0;right:0;bottom:0;height:220px;background:linear-gradient(transparent,rgba(0,0,0,.7))"></div>'
    if playing:
        src, title, artist, cov = "AHORA SUENA · USB", "Camino de noche", "Artista de ejemplo", cover(116, 58)
        bar = f'<div style="width:100%;margin-top:10px">{prog(34, 4)}</div><div style="width:100%;margin-top:3px;display:flex;justify-content:space-between;font-size:12px;color:{DIM}"><span>1:17</span><span>3:45</span></div>'
        main_ico = "pause"
    else:
        src, title, artist = "MÚSICA", "Sin reproducción", "Toca para abrir la música"
        cov = f'<div style="width:116px;height:116px;border-radius:58px;background:#1F242D;display:flex;align-items:center;justify-content:center">{ico("note", 48, DIM)}</div>'
        bar = '<div style="width:100%;margin-top:10px;height:4px"></div><div style="margin-top:3px;height:15px"></div>'
        main_ico = "play"
    music = (f'<div style="{GLASS};flex:1.05;min-width:0;padding:10px 16px;box-sizing:border-box;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center">{cov}'
             f'<div style="margin-top:8px;font-size:12px;letter-spacing:3px;color:{ACC}">{src}</div>'
             f'<div style="margin-top:2px;max-width:100%;font-size:26px;font-weight:600;color:{INK};white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{title}</div>'
             f'<div style="font-size:17px;color:{DIM}">{artist}</div>{bar}'
             f'<div style="margin-top:8px;display:flex;align-items:center;gap:18px">{btn("prev", 54)}{btn(main_ico, 68, True)}{btn("next", 54)}</div></div>')
    st = f'<div style="text-align:center;font-size:13px;color:{ACC}">{status}</div>' if status else ""
    gauge = (f'<div style="{GLASS};flex:1.15;min-width:0;margin-left:16px;padding:10px;box-sizing:border-box;display:flex;flex-direction:column;align-items:center;justify-content:center">'
             f'{speed_gauge(speed, limit, 230)}{st}<div style="margin-top:2px">{compass(heading, 64)}</div></div>')
    t, avg, mx, alt = stats
    sv, pct = service
    tripc = (f'<div style="{GLASS};flex:1;min-width:0;margin-left:16px;padding:10px 18px;box-sizing:border-box;display:flex;flex-direction:column;justify-content:center">'
             f'<div style="font-size:12px;letter-spacing:3px;color:{ACC}">VIAJE DE HOY</div>'
             f'{stat(CLOCK, "En marcha", t, "h")}{stat(AVG, "Vel. media", avg, "km/h")}{stat(PEAK, "Vel. máxima", mx, "km/h")}{stat(PEAK, "Altitud", alt, "m")}'
             f'<div style="margin-top:6px;display:flex">{odo("VIAJE", trip + " km")}{odo("TOTAL", total + " km", 1.2)}</div>'
             f'<div style="margin-top:6px;display:flex;align-items:center;gap:8px;font-size:14px;color:{DIM}">{svg(WRENCH, 18, ACC)}{sv}</div>'
             f'<div style="margin-top:5px">{prog(pct, 4)}</div></div>')
    dock = "".join(dock_app(i) for i in range(6)) + dock_all()
    return frame(f"""{bg}<div style="position:absolute;inset:0;padding:16px 30px 14px;display:flex;flex-direction:column">
  <div style="flex:1;display:flex;align-items:stretch;padding:6px 0 10px">{music}{gauge}{tripc}</div>
  <div style="display:flex;justify-content:center;gap:8px;padding-bottom:4px">{dock}</div>
</div>""", "#000")


if __name__ == "__main__":
    SHOTS.mkdir(parents=True, exist_ok=True)
    jobs = [(SHOTS / "01-inicio-reproduciendo.png", lambda: home(72, 45, 110, "18.4", "48 213")),
            (SHOTS / "02-inicio-reposo.png", lambda: home(None, None, 110, "0.0", "48 213", "Toca para activar velocímetro y odómetro", playing=False, stats=("0:00", "--", "0", "--"))),
            (SHOTS / "05-limite-de-velocidad.png", lambda: home(118, 300, 110, "52.7", "48 247", stats=("0:58", "54", "118", "1 540")))]
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(viewport={"width": 1024, "height": 600}, device_scale_factor=1)
        for path, fn in jobs:
            pg.set_content(fn()); pg.wait_for_timeout(150)
            pg.screenshot(path=str(path))
        b.close()
    print("OK")
