#!/usr/bin/env python3
"""Inicio 3.5 (diseño A): música a la izquierda; velocidad, brújula y odómetro a la derecha.

Genera docs/screenshots/01, 02 y 05 (render del diseño, no capturas del equipo).
    pip install playwright pillow && python3 docs/preview/instrumentos.py
"""
import math, pathlib, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from nocturno import INK, ring, ring_app  # noqa: E402
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


def speed_gauge(speed, limit, size=180, maxv=180, stroke=14):
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
    lim = ""
    if limit:
        a = a0 + sweep * limit / maxv; h = stroke * .75
        (px, py), (qx, qy) = pt(c, r - h, a), pt(c, r + h, a)
        tx, ty = pt(c, inner - r * .25, a)
        lim = (f'<line x1="{px:.1f}" y1="{py:.1f}" x2="{qx:.1f}" y2="{qy:.1f}" stroke="#FF5252" stroke-width="4" stroke-linecap="round"/>'
               f'<text x="{tx:.1f}" y="{ty + 5:.1f}" text-anchor="middle" font-size="{size * .075:.0f}" fill="#FF5252" font-family="Inter">{limit}</text>')
    num_col = "#FF5252" if (speed and limit and speed > limit) else INK
    label = "--" if speed is None else str(speed)
    return (f'<div style="position:relative;width:{size}px;height:{size}px;flex:none">'
            f'<svg width="{size}" height="{size}">{track}{prog_}{ticks}{lim}</svg>'
            f'<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;padding-top:8px">'
            f'<div style="font-size:{int(size*.30)}px;font-weight:300;color:{num_col};line-height:1">{label}</div>'
            f'<div style="margin-top:6px;font-size:{int(size*.085)}px;color:{DIM};letter-spacing:1px">km/h</div></div></div>')


def compass(heading, size=110):
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
        letters += f'<text x="{x:.1f}" y="{y + 5.5:.1f}" text-anchor="middle" font-size="{16 if t == "N" else 15}" font-weight="600" fill="{ACC if t == "N" else DIM}" font-family="Inter">{t}</text>'
    arrow_col = INK if heading is not None else "rgba(255,255,255,.25)"
    arrow = f'<path d="M{c},{c - rr*.42:.1f} L{c + rr*.2:.1f},{c + rr*.26:.1f} L{c},{c + rr*.14:.1f} L{c - rr*.2:.1f},{c + rr*.26:.1f} Z" fill="{arrow_col}"/>'
    text = f'{cardinal(heading)} {heading}°' if heading is not None else "—"
    return (f'<div style="flex:1;display:flex;flex-direction:column;align-items:center">'
            f'<svg width="{size}" height="{size}"><circle cx="{c}" cy="{c}" r="{rr}" fill="none" stroke="rgba(255,255,255,.15)" stroke-width="1.5"/>{ticks}{letters}{arrow}</svg>'
            f'<div style="margin-top:6px;font-size:19px;color:{INK}">{text}</div></div>')


def odo(label, value, grow=1):
    return (f'<div style="flex:{grow}"><div style="font-size:12px;letter-spacing:2px;color:{DIM}">{label}</div>'
            f'<div style="font-size:21px;color:{INK};font-variant-numeric:tabular-nums">{value}</div></div>')


def home(speed, heading, limit, trip, total, status=None, playing=True):
    bg = (f'<div style="position:absolute;inset:-80px;background:{ART};filter:blur(70px) saturate(1.2) brightness(.5)"></div>' if playing else "")
    bg += '<div style="position:absolute;left:0;right:0;bottom:0;height:220px;background:linear-gradient(transparent,rgba(0,0,0,.7))"></div>'
    if playing:
        src, title, artist, cov = "AHORA SUENA · USB", "Camino de noche", "Artista de ejemplo", cover(150, 75)
        bar = f'<div style="margin-top:12px">{prog(34, 4)}</div><div style="margin-top:4px;display:flex;justify-content:space-between;font-size:13px;color:{DIM}"><span>1:17</span><span>3:45</span></div>'
        main_ico = "pause"
    else:
        src, title, artist = "MÚSICA", "Sin reproducción", "Toca para abrir la música"
        cov = f'<div style="width:150px;height:150px;border-radius:75px;background:#1F242D;display:flex;align-items:center;justify-content:center">{ico("note", 60, DIM)}</div>'
        bar = '<div style="margin-top:12px;height:4px"></div><div style="margin-top:4px;height:17px"></div>'
        main_ico = "play"
    music = (f'<div style="flex:1;display:flex;align-items:center;padding:8px">{cov}<div style="width:320px;margin-left:24px">'
             f'<div style="font-size:13px;letter-spacing:3px;color:{ACC}">{src}</div>'
             f'<div style="margin-top:4px;font-size:32px;font-weight:600;color:{INK};line-height:1.15;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{title}</div>'
             f'<div style="font-size:19px;color:{DIM}">{artist}</div>{bar}'
             f'<div style="margin-top:10px;display:flex;align-items:center;gap:22px">{btn("prev", 60)}{btn(main_ico, 76, True)}{btn("next", 60)}</div></div></div>')
    st = f'<div style="margin-top:6px;text-align:center;font-size:14px;color:{ACC}">{status}</div>' if status else ""
    card = (f'<div style="{GLASS};width:340px;padding:16px 20px 14px;box-sizing:border-box;margin-left:26px">'
            f'<div style="display:flex;align-items:center">{speed_gauge(speed, limit)}{compass(heading)}</div>{st}'
            f'<div style="height:1px;margin-top:10px;background:rgba(255,255,255,.08)"></div>'
            f'<div style="display:flex;margin-top:10px">{odo("VIAJE", trip + " km")}{odo("TOTAL", total + " km", 1.3)}</div></div>')
    dock = "".join(ring_app(i) for i in range(6)) + ring("apps", "Todas", accent=True)
    return frame(f"""{bg}<div style="position:absolute;inset:0;padding:16px 30px 14px;display:flex;flex-direction:column">
  <div style="flex:1;display:flex;align-items:center">{music}{card}</div>
  <div style="display:flex;justify-content:center;gap:14px">{dock}</div>
</div>""", "#000")


if __name__ == "__main__":
    SHOTS.mkdir(parents=True, exist_ok=True)
    jobs = [(SHOTS / "01-inicio-reproduciendo.png", lambda: home(72, 45, 110, "18.4", "48 213")),
            (SHOTS / "02-inicio-reposo.png", lambda: home(None, None, 110, "0.0", "48 213", "Toca para activar velocímetro y odómetro", playing=False)),
            (SHOTS / "05-limite-de-velocidad.png", lambda: home(118, 300, 110, "52.7", "48 247"))]
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(viewport={"width": 1024, "height": 600}, device_scale_factor=1)
        for path, fn in jobs:
            pg.set_content(fn()); pg.wait_for_timeout(150)
            pg.screenshot(path=str(path))
        b.close()
    print("OK")
