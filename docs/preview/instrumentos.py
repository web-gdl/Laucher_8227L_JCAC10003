#!/usr/bin/env python3
"""Inicio 3.8 (7"): música 60 % y widget deslizable de 6 páginas; dock sin círculos.

Genera docs/screenshots/01, 02, 05 y 06 (render del diseño, no capturas del equipo).
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


def dock_app(i):
    return (f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px;width:104px">{aicon(i, 62)}'
            f'<div style="font-size:13px;color:{DIM};text-align:center;line-height:1.15;height:30px;overflow:hidden">{APPS[i][0]}</div></div>')


def dock_all():
    return (f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px;width:104px"><div style="width:62px;height:62px;display:flex;align-items:center;justify-content:center">{ico("apps", 44, ACC)}</div>'
            f'<div style="font-size:13px;color:{DIM};text-align:center;line-height:1.15;height:30px;overflow:hidden">Todas</div></div>')


PARAM = {}
def svg(path, s=22, col=DIM): return f'<svg width="{s}" height="{s}" viewBox="0 0 24 24" fill="{col}"><path d="{path}"/></svg>'
WRENCH="M22.7,19l-9.1,-9.1c0.9,-2.3 0.4,-5 -1.5,-6.9 -2,-2 -5,-2.4 -7.4,-1.3L9,6 6,9 1.6,4.7C0.4,7.1 0.9,10.1 2.9,12.1c1.9,1.9 4.6,2.4 6.9,1.5l9.1,9.1c0.4,0.4 1,0.4 1.4,0l2.3,-2.3c0.5,-0.4 0.5,-1.1 0.1,-1.4z"
FUEL="M19.77,7.23l0.01,-0.01 -3.72,-3.72L15,4.56l2.11,2.11C16.17,7.03 15.5,7.93 15.5,9c0,1.38 1.12,2.5 2.5,2.5 0.36,0 0.69,-0.08 1,-0.21v7.21c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V14c0,-1.1 -0.9,-2 -2,-2h-1V5c0,-1.1 -0.9,-2 -2,-2H6c-1.1,0 -2,0.9 -2,2v16h10v-7.5h1.5v5c0,1.38 1.12,2.5 2.5,2.5s2.5,-1.12 2.5,-2.5V9c0,-0.69 -0.28,-1.32 -0.73,-1.77zM12,10H6V5h6v5zM18,10c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1z"
GEAR="M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94 0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94l-0.36,-2.54c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41l-0.36,2.54c-0.59,0.24 -1.13,0.57 -1.62,0.94l-2.39,-0.96c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87c-0.12,0.21 -0.08,0.47 0.12,0.61l2.03,1.58c-0.05,0.3 -0.09,0.63 -0.09,0.94s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61l-2.01,-1.58zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6 3.6,1.62 3.6,3.6 -1.62,3.6 -3.6,3.6z"
NAMES=["Velocímetro","Odómetro","Consumo","Servicio","Viaje de hoy","Brújula"]
def bg():
    return (f'<div style="position:absolute;inset:-80px;background:{ART};filter:blur(70px) saturate(1.2) brightness(.5)"></div>'
            '<div style="position:absolute;left:0;right:0;bottom:0;height:220px;background:linear-gradient(transparent,rgba(0,0,0,.7))"></div>')
def music(w=None):
    return (f'<div style="{GLASS};flex:6;min-width:0;padding:16px 24px;box-sizing:border-box;display:flex;align-items:center;gap:26px">'
            f'<div style="display:flex;flex-direction:column;align-items:center">{cover(190,95)}</div>'
            f'<div style="flex:1;min-width:0"><div style="font-size:14px;letter-spacing:3px;color:{ACC}">AHORA SUENA · USB</div>'
            f'<div style="margin-top:4px;font-size:32px;font-weight:600;color:{INK};line-height:1.1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Camino de noche</div>'
            f'<div style="font-size:22px;color:{DIM};margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Artista de ejemplo · Álbum</div>'
            f'<div style="margin-top:16px">{prog(34,6)}</div><div style="display:flex;justify-content:space-between;font-size:15px;color:{DIM};margin-top:4px"><span>1:17</span><span>3:45</span></div>'
            f'<div style="margin-top:14px;display:flex;align-items:center;justify-content:center;gap:24px">{btn("prev",72)}{btn("pause",92,True)}{btn("next",72)}</div></div></div>')
def big(label, val, unit="", col=INK, size=64):
    return (f'<div style="text-align:center"><div style="font-size:15px;letter-spacing:3px;color:{DIM}">{label}</div>'
            f'<div style="font-size:{size}px;line-height:1.05;color:{col};font-variant-numeric:tabular-nums">{val}<span style="font-size:{int(size*.38)}px;color:{DIM};margin-left:6px">{unit}</span></div></div>')
def row(label, val, unit=""):
    return (f'<div style="display:flex;align-items:baseline;justify-content:space-between;padding:11px 0;border-bottom:1px solid rgba(255,255,255,.07)">'
            f'<span style="font-size:19px;color:{DIM}">{label}</span><span style="font-size:34px;color:{INK};font-variant-numeric:tabular-nums">{val}<span style="font-size:17px;color:{DIM};margin-left:5px">{unit}</span></span></div>')
def page(i):
    if i==0: return speed_gauge(PARAM["speed"],110,300) + (f'<div style="font-size:14px;color:{ACC};text-align:center">{PARAM["status"]}</div>' if PARAM["status"] else "")
    if i==1: return (f'<div style="display:flex;flex-direction:column;gap:26px;width:100%">{big("VIAJE","18.4","km",INK,78)}{big("TOTAL","48 213","km",INK,78)}'
                     f'<div style="text-align:center;font-size:15px;color:{DIM}">Mantén presionado: reiniciar o ajustar</div></div>')
    if i==2: return (f'<div style="width:100%;display:flex;flex-direction:column;align-items:center;gap:8px">{big("PROMEDIO","11.8","km/L",INK,70)}'
                     f'<div style="width:100%">{row("Última carga","32.5","L")}{row("Costo por km","$1.74")}{row("Autonomía","412","km")}</div></div>')
    if i==3: return (f'<div style="width:100%;display:flex;flex-direction:column;align-items:center;gap:16px">{svg(WRENCH,44,ACC)}{big("SERVICIO EN","1 240","km",INK,84)}'
                     f'<div style="width:100%">{prog(88,10)}</div><div style="font-size:17px;color:{DIM}">Cada 10 000 km · último a los 38 973 km</div>'
                     f'<div style="padding:12px 26px;border-radius:30px;background:rgba(255,179,71,.16);border:1px solid {ACC};color:{ACC};font-size:19px">Servicio hecho hoy</div></div>')
    if i==4: return (f'<div style="width:100%">{row("En marcha","0:42","h")}{row("Vel. media","54","km/h")}{row("Vel. máxima","118","km/h")}{row("Altitud","1 540","m")}{row("Distancia","18.4","km")}</div>')
    return (f'<div style="display:flex;flex-direction:column;align-items:center;gap:6px">{compass(PARAM["heading"],250)}</div>')
def widget(i, extra=""):
    dots="".join(f'<div style="width:{26 if k==i else 10}px;height:10px;border-radius:5px;background:{ACC if k==i else "rgba(255,255,255,.3)"}"></div>' for k in range(6))
    return (f'<div style="{GLASS};flex:4;min-width:0;margin-left:16px;padding:14px 20px 12px;box-sizing:border-box;display:flex;flex-direction:column;position:relative">'
            f'<div style="display:flex;justify-content:space-between;align-items:center;height:26px"><span style="font-size:14px;letter-spacing:3px;color:{ACC};text-transform:uppercase">{NAMES[i]}</span>'
            f'<span style="display:flex;align-items:center;gap:6px;font-size:12px;color:{DIM}">{svg(GEAR,16,DIM)}mantén</span></div>'
            f'<div style="flex:1;display:flex;align-items:center;justify-content:center;min-height:0">{page(i)}</div>'
            f'<div style="display:flex;justify-content:center;align-items:center;gap:8px;height:16px">{dots}</div>{extra}</div>')
def home_page(i, extra="", speed=72, heading=45, playing=True, status=None):
    PARAM.update(speed=speed, heading=heading, status=status)
    dock="".join(dock_app(k) for k in range(6))+dock_all()
    return frame(f'{bg()}<div style="position:absolute;inset:0;padding:16px 30px 14px;display:flex;flex-direction:column">'
                 f'<div style="flex:1;display:flex;align-items:stretch;padding:6px 0 28px">{music()}{widget(i, extra)}</div>'
                 f'<div style="display:flex;justify-content:center;gap:8px;padding-bottom:4px">{dock}</div></div>',"#000")
def sheet():
    cells="".join(f'<div style="width:360px;height:388px;position:relative;display:flex">{widget(i).replace("flex:4","flex:1").replace("margin-left:16px","margin-left:0")}</div>' for i in range(6))
    return (f'<body style="margin:0;background:#0B0D12;width:1160px;font-family:Roboto,sans-serif"><div style="padding:22px;display:grid;grid-template-columns:repeat(3,360px);gap:18px">{cells}</div></body>')

if __name__ == "__main__":
    SHOTS.mkdir(parents=True, exist_ok=True)
    jobs = [(SHOTS / "01-inicio-reproduciendo.png", lambda: home_page(0, speed=72)),
            (SHOTS / "02-inicio-reposo.png", lambda: home_page(0, speed=None, status="Toca para activar velocímetro y odómetro")),
            (SHOTS / "05-limite-de-velocidad.png", lambda: home_page(0, speed=118))]
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(viewport={"width": 1024, "height": 600}, device_scale_factor=1)
        for path, fn in jobs:
            pg.set_content(fn()); pg.wait_for_timeout(150)
            pg.screenshot(path=str(path))
        pg2 = b.new_page(viewport={"width": 1160, "height": 900}, device_scale_factor=1)
        pg2.set_content(sheet()); pg2.wait_for_timeout(150)
        pg2.screenshot(path=str(SHOTS / "06-widgets-deslizables.png"), full_page=True)
        b.close()
    print("OK")
