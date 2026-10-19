#!/usr/bin/env python3
"""Bocetos: 20 propuestas de rediseño a la medida real: barra del sistema (66 px) + área del launcher 1024x534.
Sin reloj ni iconos de estado (ya están en la barra del sistema). Etiquetas a dos líneas."""
import pathlib, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from render import C, P, ico  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

OUT = pathlib.Path(__file__).resolve().parent.parent / "bocetos"
BAR = 66
H = 600 - BAR
TXT, DIM, ACC, SURF, HIGH, OUTL, BG = C["text"], C["dim"], C["accent"], C["surface"], C["high"], C["outline"], C["bg"]
ART = "linear-gradient(135deg,#3B2A5C 0%,#B4505A 55%,#FFB74D 100%)"

P.update(
    home="M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z",
    recents="M3,7h13v13H3zM7,3h14v14h-2V5H7z",
    up="M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6 -6,6z",
    usb="M15,7v4h1v2h-3V5h2l-3,-4 -3,4h2v8H8v-2.07c0.7,-0.37 1.2,-1.08 1.2,-1.93 0,-1.21 -0.99,-2.2 -2.2,-2.2 -1.21,0 -2.2,0.99 -2.2,2.2 0,0.85 0.5,1.56 1.2,1.93V13c0,1.11 0.89,2 2,2h3v3.05c-0.71,0.37 -1.2,1.1 -1.2,1.95 0,1.22 0.99,2.2 2.2,2.2 1.21,0 2.2,-0.98 2.2,-2.2 0,-0.85 -0.49,-1.58 -1.2,-1.95V15h3c1.11,0 2,-0.89 2,-2v-2h1V7h-4z",
    cast="M1,18v3h3c0,-1.66 -1.34,-3 -3,-3zm0,-4v2c2.76,0 5,2.24 5,5h2c0,-3.87 -3.13,-7 -7,-7zm0,-4v2c4.97,0 9,4.03 9,9h2c0,-6.08 -4.93,-11 -11,-11zm20,-7H3c-1.1,0 -2,0.9 -2,2v3h2V5h18v14h-7v2h7c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z",
    gps="M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zm0,9.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z",
    sysback="M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z",
    vol="M3,9v6h4l5,5V4L7,9H3zm13.5,3c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z",
    queue="M3,13h2v-2H3v2zm0,4h2v-2H3v2zm0,-8h2V7H3v2zm4,4h14v-2H7v2zm0,4h14v-2H7v2zM7,7v2h14V7H7z",
    shuffle="M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5zm0.33,9.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04 -3.13,-3.13z",
    repeat="M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zm10,10H7v-3l-4,4 4,4v-3h12v-6h-2v4z",
    eq="M10,20h4V4h-4v16zm-6,0h4v-8H4v8zM16,9v11h4V9h-4z",
    star="M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z",
    chev="M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z",
    usbsrc="M15,7v4h1v2h-3V5h2l-3,-4 -3,4h2v8H8v-2.07c0.7,-0.37 1.2,-1.08 1.2,-1.93 0,-1.21 -0.99,-2.2 -2.2,-2.2 -1.21,0 -2.2,0.99 -2.2,2.2 0,0.85 0.5,1.56 1.2,1.93V13c0,1.11 0.89,2 2,2h3v3.05c-0.71,0.37 -1.2,1.1 -1.2,1.95 0,1.22 0.99,2.2 2.2,2.2 1.21,0 2.2,-0.98 2.2,-2.2 0,-0.85 -0.49,-1.58 -1.2,-1.95V15h3c1.11,0 2,-0.89 2,-2v-2h1V7h-4z",
    globe="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zm6.93,6h-2.95c-0.32,-1.25 -0.78,-2.45 -1.38,-3.56 1.84,0.63 3.37,1.91 4.33,3.56zM12,4.04c0.83,1.2 1.48,2.53 1.91,3.96h-3.82c0.43,-1.43 1.08,-2.76 1.91,-3.96zM4.26,14C4.1,13.36 4,12.69 4,12s0.1,-1.36 0.26,-2h3.38c-0.08,0.66 -0.14,1.32 -0.14,2 0,0.68 0.06,1.34 0.14,2H4.26zm0.82,2h2.95c0.32,1.25 0.78,2.45 1.38,3.56 -1.84,-0.63 -3.37,-1.9 -4.33,-3.56zm2.95,-8H5.08c0.96,-1.66 2.49,-2.93 4.33,-3.56C8.81,5.55 8.35,6.75 8.03,8zM12,19.96c-0.83,-1.2 -1.48,-2.53 -1.91,-3.96h3.82c-0.43,1.43 -1.08,2.76 -1.91,3.96zM14.34,14H9.66c-0.09,-0.66 -0.16,-1.32 -0.16,-2 0,-0.68 0.07,-1.35 0.16,-2h4.68c0.09,0.65 0.16,1.32 0.16,2 0,0.68 -0.07,1.34 -0.16,2zm0.25,5.56c0.6,-1.11 1.06,-2.31 1.38,-3.56h2.95c-0.96,1.65 -2.49,2.93 -4.33,3.56zM16.36,14c0.08,-0.66 0.14,-1.32 0.14,-2 0,-0.68 -0.06,-1.34 -0.14,-2h3.38c0.16,0.64 0.26,1.31 0.26,2s-0.1,1.36 -0.26,2h-3.38z",
    store="M18,6h-2c0,-2.21 -1.79,-4 -4,-4S8,3.79 8,6H6c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2zm-6,-2c1.1,0 2,0.9 2,2h-4c0,-1.1 0.9,-2 2,-2zm6,16H6V8h12v12z",
)

# Apps de ejemplo con nombres largos a propósito (para probar las etiquetas a dos líneas)
APPS = [("Música", "note", "#EF6C00"), ("Radio FM", "radio", "#00897B"), ("BT música", "bt", "#1E88E5"),
        ("Llamada BT", "phone", "#43A047"), ("Navegación", "nav", "#3949AB"), ("Gestor de archivos", "folder", "#F9A825"),
        ("Reproductor de vídeo", "video", "#8E24AA"), ("Navegador web", "globe", "#0097A7"), ("Tienda de aplicaciones", "store", "#5E35B1"),
        ("Ajustes", "settings", "#546E7A"), ("Cámara de reversa", "cam", "#6D4C41")]

CSS = f"""*{{box-sizing:border-box;margin:0;padding:0}}
html,body{{width:1024px;height:600px;overflow:hidden;background:{BG};font-family:'Inter',sans-serif;color:{TXT}}}
.l2{{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;text-align:center;line-height:1.2}}
.l1{{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}}"""


def sysbar():
    L = "".join(ico(n, 34, "#fff") + '<span style="width:34px"></span>' for n in ["home", "recents", "up"]) + ico("usb", 28, "#fff")
    R = "".join(ico(n, 34, "#fff") + '<span style="width:26px"></span>' for n in ["cast", "gps", "wifi"]) + ico("sysback", 34, "#fff")
    return (f'<div style="position:absolute;left:0;right:0;top:0;height:{BAR}px;background:#000;display:flex;align-items:center;padding:0 26px">'
            f'{L}<span style="flex:1"></span><span style="font-size:26px;font-weight:600;color:#fff">8:10 p. m.</span><span style="flex:1"></span>{R}</div>')


def frame(inner, bg=BG):
    return (f"<!doctype html><meta charset=utf-8><style>{CSS}</style>{sysbar()}"
            f'<div style="position:absolute;left:0;right:0;top:{BAR}px;height:{H}px;background:{bg};overflow:hidden">{inner}</div>')


def aicon(i, s):
    _, g, col = APPS[i]
    return (f'<div style="width:{s}px;height:{s}px;border-radius:{s*.22}px;background:{col};flex:none;display:flex;align-items:center;'
            f'justify-content:center">{ico(g, s*.58, "#fff")}</div>')


def cover(s, r=18):
    return (f'<div style="width:{s}px;height:{s}px;border-radius:{r}px;background:{ART};flex:none;display:flex;align-items:center;'
            f'justify-content:center">{ico("note", s*.32, "rgba(255,255,255,.55)")}</div>')


def btn(n, s, a=False, bg=None, fg=None):
    b = bg or (ACC if a else HIGH); f = fg or (BG if a else TXT)
    return f'<div style="width:{s}px;height:{s}px;border-radius:50%;background:{b};flex:none;display:flex;align-items:center;justify-content:center">{ico(n, s*.5, f)}</div>'


def prog(p=34, h=6, track=OUTL, fill=ACC):
    return f'<div style="height:{h}px;border-radius:{h/2}px;background:{track}"><div style="width:{p}%;height:{h}px;border-radius:{h/2}px;background:{fill}"></div></div>'


def card(inner, st=""):
    return f'<div style="background:{SURF};border:1px solid {OUTL};border-radius:22px;{st}">{inner}</div>'


def tile(i, s=64, fs=15, st=""):
    return (f'<div style="background:{HIGH};border-radius:18px;display:flex;flex-direction:column;align-items:center;justify-content:center;'
            f'gap:8px;padding:10px 8px;{st}">{aicon(i, s)}<div class="l2" style="font-size:{fs}px;max-width:100%">{APPS[i][0]}</div></div>')


def title_block(ts=28, a=18, src="MÚSICA · USB"):
    return (f'<div style="font-size:13px;letter-spacing:2px;color:{ACC}">{src}</div>'
            f'<div class="l1" style="margin-top:4px;font-size:{ts}px;font-weight:600">Camino de noche</div>'
            f'<div class="l1" style="font-size:{a}px;color:{DIM}">Artista de ejemplo</div>')


def controls(s1=60, s2=76, gap=22):
    return f'<div style="display:flex;align-items:center;justify-content:center;gap:{gap}px">{btn("prev", s1)}{btn("pause", s2, True)}{btn("next", s1)}</div>'


# ============================================================ RECREADAS (01–10)

def r01_conduccion():  # A
    dock = "".join(f'<div style="flex:1;background:{SURF};border-radius:18px;display:flex;align-items:center;gap:10px;padding:0 12px">{aicon(i,44)}'
                   f'<div style="font-size:15px;line-height:1.15;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">{APPS[i][0]}</div></div>' for i in [0, 1, 2, 3, 4, 5])
    dock += f'<div style="width:90px;background:{SURF};border-radius:18px;display:flex;align-items:center;justify-content:center">{ico("apps",32,ACC)}</div>'
    return frame(f"""<div style="position:absolute;inset:0;padding:20px;display:flex;flex-direction:column;gap:16px">
  {card(f'<div style="display:flex;gap:30px;align-items:center;height:100%">{cover(300,22)}<div style="flex:1;min-width:0">{title_block(40,24)}<div style="margin-top:26px">{prog(34,8)}</div><div style="margin-top:8px;display:flex;justify-content:space-between;font-size:15px;color:{DIM}"><span>1:17</span><span>3:45</span></div><div style="margin-top:20px;display:flex;gap:30px">{btn("prev",84)}{btn("pause",104,True)}{btn("next",84)}</div></div></div>','flex:1;min-height:0;padding:26px')}
  <div style="height:84px;display:flex;gap:12px">{dock}</div></div>""")


def r02_dock_lateral():  # B
    rail = "".join(f'<div style="width:72px;height:66px;border-radius:18px;{"background:"+HIGH+";" if a else ""}display:flex;align-items:center;justify-content:center">{ico(n,32,ACC if a else DIM)}</div>'
                   for n, a in [("note", True), ("radio", False), ("phone", False), ("nav", False), ("apps", False), ("settings", False)])
    tiles = "".join(tile(i, 58) for i in [0, 1, 2, 5, 6, 7])
    return frame(f"""<div style="position:absolute;inset:0;display:flex">
  <div style="width:100px;background:{SURF};border-right:1px solid {OUTL};display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px">{rail}</div>
  <div style="flex:1;padding:20px;display:flex;flex-direction:column;gap:16px">
    {card(f'<div style="display:flex;gap:22px;align-items:center">{cover(150)}<div style="flex:1;min-width:0">{title_block(28,18)}<div style="margin-top:16px">{prog()}</div></div>{controls(62,80,14)}</div>','height:190px;padding:20px;display:flex;align-items:center')}
    <div style="flex:1;min-height:0;display:grid;grid-template-columns:repeat(3,1fr);grid-template-rows:1fr 1fr;gap:14px">{tiles}</div>
  </div></div>""")


def r03_tres_columnas():  # C (sin reloj: columna izquierda = accesos rápidos)
    rows = "".join(f'<div style="flex:1;background:{HIGH};border-radius:16px;display:flex;align-items:center;gap:14px;padding:0 14px">{aicon(i,42)}<div class="l1" style="font-size:18px;flex:1">{APPS[i][0]}</div>{ico("chev",24,DIM)}</div>' for i in [0, 1, 2, 3, 4, 5])
    quick = "".join(f'<div style="flex:1;background:{HIGH};border-radius:16px;display:flex;align-items:center;gap:12px;padding:0 16px;font-size:16px">{ico(n,26,c)}{t}</div>'
                    for n, t, c in [("power", "Apagar pantalla", TXT), ("apps", "Todas las apps", ACC), ("settings", "Ajustes", TXT), ("info", "Diagnóstico", TXT)])
    return frame(f"""<div style="position:absolute;inset:0;padding:20px;display:flex;gap:16px">
  <div style="width:230px;display:flex;flex-direction:column;gap:10px">{quick}</div>
  {card(f'{cover(220,20)}<div style="margin-top:14px;width:100%;text-align:center">{title_block(24,17)}</div><div style="margin-top:12px;width:100%">{prog()}</div><div style="flex:1"></div>{controls(58,74,18)}','width:300px;padding:22px;display:flex;flex-direction:column;align-items:center')}
  <div style="flex:1;display:flex;flex-direction:column;gap:10px">{rows}</div></div>""")


def r04_portada_fondo():  # 05
    apps = "".join(f'<div style="display:flex;flex-direction:column;align-items:center;gap:6px;width:92px">{aicon(i,56)}<div class="l2" style="font-size:13px">{APPS[i][0]}</div></div>' for i in [0, 1, 2, 3, 4, 5])
    return frame(f"""<div style="position:absolute;inset:-60px;background:{ART};filter:blur(60px);opacity:.55"></div>
<div style="position:absolute;inset:0;background:linear-gradient(90deg,rgba(11,13,17,.3),rgba(11,13,17,.85))"></div>
<div style="position:absolute;inset:0;padding:26px;display:flex;gap:34px">
  <div style="display:flex;flex-direction:column;justify-content:center;gap:18px">{cover(290,20)}{controls(62,80)}</div>
  <div style="flex:1;display:flex;flex-direction:column;min-width:0">
    <div style="margin-top:20px">{title_block(40,24,'AHORA SUENA')}</div><div style="margin-top:22px">{prog(34,6)}</div>
    <div style="flex:1"></div>
    <div style="display:flex;justify-content:space-between;background:rgba(11,13,17,.55);border-radius:22px;padding:16px 14px">{apps}</div>
  </div></div>""")


def r05_carrusel():  # 07
    side = lambda t, n: f'<div style="width:150px;height:400px;border-radius:28px;background:{SURF};border:1px solid {OUTL};opacity:.5;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px">{ico(n,46,DIM)}<span style="color:{DIM};font-size:17px">{t}</span></div>'
    dots = "".join(f'<div style="width:{26 if k==1 else 10}px;height:10px;border-radius:5px;background:{ACC if k==1 else OUTL}"></div>' for k in range(4))
    return frame(f"""<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18px">
  <div style="display:flex;align-items:center;gap:24px">{side('Teléfono','phone')}
    {card(f'{cover(180,22)}<div style="margin-top:14px;text-align:center">{title_block(30,19)}</div><div style="margin-top:14px;width:440px">{prog()}</div><div style="margin-top:16px">{controls(62,82,28)}</div>','width:600px;height:450px;border-radius:32px;padding:24px;display:flex;flex-direction:column;align-items:center')}
  {side('Apps','apps')}</div><div style="display:flex;gap:10px">{dots}</div></div>""")


def r06_alto_contraste():  # 08
    item = lambda t, a=False: f'<div style="border:3px solid {"#FFC107" if a else "#fff"};border-radius:14px;display:flex;align-items:center;justify-content:center;font-size:32px;font-weight:800;color:{"#FFC107" if a else "#fff"};text-align:center;line-height:1.1;padding:6px">{t}</div>'
    return frame(f"""<div style="position:absolute;inset:0;padding:20px;display:flex;flex-direction:column;gap:16px">
  <div style="border:3px solid #fff;border-radius:14px;padding:14px 22px;display:flex;align-items:center;gap:22px">
    <div style="flex:1;min-width:0"><div class="l1" style="font-size:36px;font-weight:800">CAMINO DE NOCHE</div><div style="font-size:24px;font-weight:600">Artista de ejemplo</div></div>
    {ico('prev',64,'#fff')}<div style="width:96px;height:96px;border-radius:50%;background:#FFC107;display:flex;align-items:center;justify-content:center">{ico('pause',60,'#000')}</div>{ico('next',64,'#fff')}</div>
  <div style="flex:1;display:grid;grid-template-columns:repeat(3,1fr);grid-template-rows:1fr 1fr;gap:14px">
    {item('MÚSICA',True)}{item('RADIO')}{item('LLAMADAS')}{item('MAPA')}{item('ARCHIVOS')}{item('APPS')}</div></div>""", "#000")


def r07_botonera():  # 09
    return frame(f"""<div style="position:absolute;inset:0;padding:18px;display:flex;flex-direction:column;gap:14px">
  <div style="height:84px;background:{HIGH};border-radius:22px;display:flex;align-items:center;padding:0 14px;gap:16px">{cover(60,14)}
    <div style="flex:1;min-width:0"><div class="l1" style="font-size:20px;font-weight:600">Camino de noche</div><div style="font-size:15px;color:{DIM}">Artista de ejemplo · 1:17 / 3:45</div></div>{controls(56,66,14)}</div>
  <div style="flex:1;display:grid;grid-template-columns:repeat(3,1fr);grid-template-rows:1fr 1fr;gap:14px">{''.join(tile(i,80,20) for i in [0,1,3,4,2,5])}</div></div>""")


def r08_alcance():  # 12
    ctrl = "".join(f'<div style="height:96px;border-radius:22px;background:{ACC if n=="pause" else HIGH};display:flex;align-items:center;justify-content:center">{ico(n,46,BG if n=="pause" else TXT)}</div>' for n in ["prev", "pause", "next"])
    apps = "".join(f'<div style="flex:1;background:{SURF};border-radius:16px;display:flex;align-items:center;gap:12px;padding:0 14px">{aicon(i,40)}<div class="l1" style="font-size:16px">{APPS[i][0]}</div></div>' for i in [0, 1, 2, 3, 4, 5, 6])
    return frame(f"""<div style="position:absolute;inset:0;display:flex">
  <div style="width:150px;padding:16px;display:flex;flex-direction:column;gap:14px;background:{SURF};border-right:1px solid {OUTL}">
    <div style="font-size:12px;letter-spacing:2px;color:{DIM};text-align:center">AL ALCANCE</div>{ctrl}<div style="flex:1"></div>
    <div style="height:72px;border-radius:22px;background:{HIGH};display:flex;align-items:center;justify-content:center">{ico('apps',34,ACC)}</div></div>
  <div style="flex:1;padding:22px;display:flex;gap:22px">
    <div style="flex:1;display:flex;flex-direction:column;justify-content:center;gap:20px;min-width:0">{cover(240,22)}<div>{title_block(32,20)}</div><div>{prog()}</div></div>
    <div style="width:270px;display:flex;flex-direction:column;gap:9px">{apps}</div></div></div>""")


def r09_modo_dia():  # 13
    L = dict(surf="#FFFFFF", high="#E3E8EF", text="#14171C", dim="#5B6575", acc="#E07B00", outl="#D5DBE4")
    favs = "".join(f'<div style="background:{L["surf"]};border:1px solid {L["outl"]};border-radius:20px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;padding:8px">{aicon(i,62)}<div class="l2" style="font-size:15px;color:{L["text"]}">{APPS[i][0]}</div></div>' for i in [0, 1, 2, 3, 4, 5, 6, 7])
    b = lambda n, s, a=False: btn(n, s, a, L["acc"] if a else L["high"], "#fff" if a else L["text"])
    return frame(f"""<div style="position:absolute;inset:0;padding:20px;display:flex;gap:18px;color:{L['text']}">
  <div style="width:360px;background:{L['surf']};border:1px solid {L['outl']};border-radius:22px;padding:22px;display:flex;flex-direction:column;align-items:center">
    {cover(200,20)}<div style="margin-top:14px;font-size:13px;letter-spacing:2px;color:{L['acc']};font-weight:600">MÚSICA</div>
    <div style="font-size:24px;font-weight:600">Camino de noche</div><div style="color:{L['dim']}">Artista de ejemplo</div>
    <div style="margin-top:12px;width:100%">{prog(34,6,L['high'],L['acc'])}</div><div style="flex:1"></div>
    <div style="display:flex;gap:22px;align-items:center">{b('prev',58)}{b('pause',74,True)}{b('next',58)}</div></div>
  <div style="flex:1;display:grid;grid-template-columns:repeat(4,1fr);grid-template-rows:1fr 1fr;gap:14px">{favs}</div></div>""", "#EEF1F5")


def r10_bento():  # 14 sin reloj
    quick = "".join(f'<div style="background:{HIGH};border-radius:16px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px">{ico(n,28,c)}<span style="font-size:13px;color:{DIM}">{t}</span></div>'
                    for n, t, c in [("power", "Pantalla", TXT), ("vol", "Volumen", TXT), ("settings", "Ajustes", TXT), ("info", "Diagnóstico", TXT)])
    return frame(f"""<div style="position:absolute;inset:0;padding:18px;display:grid;gap:14px;grid-template-columns:1fr 1fr 1fr;grid-template-rows:1fr 1fr 0.8fr;
     grid-template-areas:'music music fav1' 'music music fav2' 'phone quick apps'">
  {card(f'<div style="display:flex;gap:24px;align-items:center;width:100%">{cover(210,20)}<div style="flex:1;min-width:0">{title_block(30,19)}<div style="margin-top:16px">{prog()}</div><div style="margin-top:18px;display:flex;gap:18px">{btn("prev",58)}{btn("pause",74,True)}{btn("next",58)}</div></div></div>','grid-area:music;padding:22px;display:flex;align-items:center')}
  {tile(1,58,15,'grid-area:fav1')}{tile(4,58,15,'grid-area:fav2')}
  {card(f'<div style="display:flex;align-items:center;gap:14px">{aicon(3,48)}<div><div style="font-size:17px">Llamada BT</div><div style="font-size:13px;color:{DIM}">Teléfono conectado</div></div></div>','grid-area:phone;padding:16px;display:flex;align-items:center')}
  <div style="grid-area:quick;display:grid;grid-template-columns:repeat(4,1fr);gap:8px">{quick}</div>
  {card(f'<div style="display:flex;gap:10px;align-items:center">{aicon(0,42)}{aicon(2,42)}{aicon(5,42)}<div style="width:42px;height:42px;border-radius:10px;background:{HIGH};display:flex;align-items:center;justify-content:center">{ico("apps",24,ACC)}</div></div>','grid-area:apps;padding:16px;display:flex;align-items:center;justify-content:center')}</div>""")


# ============================================================ NUEVAS (11–20)

def n11_cola():  # lista de lo que viene
    q = "".join(f'<div style="height:58px;display:flex;align-items:center;gap:14px;padding:0 14px;border-radius:14px;{"background:"+HIGH+";" if k==0 else ""}">'
                f'<span style="width:22px;color:{ACC if k==0 else DIM};font-size:15px">{k+1}</span><div style="flex:1;min-width:0"><div class="l1" style="font-size:17px;{"color:"+ACC if k==0 else ""}">{t}</div>'
                f'<div style="font-size:13px;color:{DIM}">{a}</div></div><span style="color:{DIM};font-size:14px">{d}</span></div>'
                for k, (t, a, d) in enumerate([("Camino de noche", "Artista de ejemplo", "3:45"), ("Luces de carretera", "Artista de ejemplo", "4:02"), ("Kilómetro cero", "Otro artista", "3:18"),
                                               ("Volver a casa", "Otro artista", "5:11"), ("Madrugada", "Tercer artista", "2:59"), ("Último tramo", "Tercer artista", "3:40")]))
    return frame(f"""<div style="position:absolute;inset:0;padding:20px;display:flex;gap:18px">
  {card(f'{cover(230,20)}<div style="margin-top:14px;width:100%">{title_block(26,18)}</div><div style="margin-top:12px;width:100%">{prog()}</div><div style="flex:1"></div><div style="display:flex;align-items:center;gap:14px">{ico("shuffle",26,DIM)}{controls(56,72,14)}{ico("repeat",26,ACC)}</div>','width:380px;padding:22px;display:flex;flex-direction:column;align-items:center')}
  {card(f'<div style="display:flex;align-items:center;gap:10px;margin:2px 4px 10px;color:{DIM};font-size:14px;letter-spacing:2px">{ico("queue",22,DIM)}A CONTINUACIÓN</div>{q}','flex:1;padding:14px')}</div>""")


def n12_fuentes():  # selector de fuente de audio
    src = "".join(f'<div style="flex:1;height:78px;border-radius:18px;background:{ACC if a else SURF};border:1px solid {OUTL};display:flex;align-items:center;justify-content:center;gap:12px;font-size:18px;{"color:"+BG+";font-weight:600" if a else ""}">{ico(n,30,BG if a else TXT)}{t}</div>'
                  for n, t, a in [("usbsrc", "USB", True), ("radio", "Radio", False), ("bt", "Bluetooth", False), ("note", "Apps", False)])
    favs = "".join(tile(i, 52, 14) for i in [3, 4, 5, 6, 7, 9])
    return frame(f"""<div style="position:absolute;inset:0;padding:20px;display:flex;flex-direction:column;gap:16px">
  <div style="display:flex;gap:12px">{src}</div>
  <div style="flex:1;display:flex;gap:16px;min-height:0">
    {card(f'<div style="display:flex;gap:22px;align-items:center;height:100%">{cover(200,20)}<div style="flex:1;min-width:0">{title_block(30,19,"USB · CARPETA ROCK")}<div style="margin-top:16px">{prog()}</div><div style="margin-top:18px;display:flex;gap:18px">{btn("prev",60)}{btn("pause",76,True)}{btn("next",60)}</div></div></div>','flex:1.4;padding:22px')}
    <div style="flex:1;display:grid;grid-template-columns:repeat(3,1fr);grid-template-rows:1fr 1fr;gap:12px">{favs}</div></div></div>""")


def n13_radio():  # dial de radio con presintonías
    marks = "".join(f'<div style="position:absolute;left:{k*1.0}%;bottom:0;width:2px;height:{30 if k%10==0 else 14}px;background:{TXT if k%10==0 else OUTL}"></div>' for k in range(0, 101, 2))
    nums = "".join(f'<div style="position:absolute;left:{(f-88)/20*100}%;top:0;transform:translateX(-50%);font-size:15px;color:{DIM}">{f}</div>' for f in range(88, 109, 4))
    pres = "".join(f'<div style="height:64px;border-radius:16px;background:{ACC if k==2 else HIGH};display:flex;flex-direction:column;align-items:center;justify-content:center;{"color:"+BG if k==2 else ""}"><span style="font-size:20px;font-weight:600">{f}</span><span style="font-size:12px;opacity:.7">P{k+1}</span></div>'
                   for k, f in enumerate(["88.9", "93.7", "97.3", "101.1", "104.5", "107.9"]))
    return frame(f"""<div style="position:absolute;inset:0;padding:22px;display:flex;flex-direction:column;gap:18px">
  {card(f'<div style="display:flex;align-items:center;gap:30px"><div><div style="font-size:14px;letter-spacing:2px;color:{ACC}">RADIO FM · ESTÉREO</div><div style="font-size:96px;font-weight:200;line-height:1">97.3<span style="font-size:30px;color:{DIM}"> MHz</span></div><div style="font-size:19px;color:{DIM}">Emisora de ejemplo</div></div><div style="flex:1"></div>{btn("prev",70)}{btn("pause",88,True)}{btn("next",70)}</div><div style="position:relative;height:64px;margin-top:22px">{nums}<div style="position:absolute;left:0;right:0;bottom:0;height:34px">{marks}</div><div style="position:absolute;left:{(97.3-88)/20*100}%;bottom:0;width:4px;height:52px;background:{ACC};border-radius:2px"></div></div>','padding:24px')}
  <div style="display:grid;grid-template-columns:repeat(6,1fr);gap:12px">{pres}</div>
  <div style="flex:1;display:flex;gap:12px">{''.join(f'<div style="flex:1;background:{SURF};border-radius:16px;display:flex;align-items:center;justify-content:center;gap:10px">{aicon(i,36)}<span class="l1" style="font-size:15px">{APPS[i][0]}</span></div>' for i in [0,2,3,4])}</div></div>""")


def n14_apps_primero():  # cuadrícula grande + mini reproductor abajo
    grid = "".join(tile(i, 62, 15) for i in range(10))
    return frame(f"""<div style="position:absolute;inset:0;padding:18px;display:flex;flex-direction:column;gap:14px">
  <div style="flex:1;display:grid;grid-template-columns:repeat(5,1fr);grid-template-rows:1fr 1fr;gap:12px">{grid}</div>
  <div style="height:88px;background:{SURF};border:1px solid {OUTL};border-radius:22px;display:flex;align-items:center;gap:16px;padding:0 14px">{cover(62,14)}
    <div style="width:280px;min-width:0"><div class="l1" style="font-size:19px;font-weight:600">Camino de noche</div><div style="font-size:15px;color:{DIM}">Artista de ejemplo</div></div>
    <div style="flex:1">{prog()}</div>{controls(54,66,12)}</div></div>""")


def n15_espectro():  # música con visualizador (barras)
    import math
    bars = "".join(f'<div style="flex:1;border-radius:4px 4px 0 0;height:{int(14+110*abs(math.sin(k*0.37)*math.cos(k*0.11))+20*((k*7)%5)/5)}px;'
                   f'background:linear-gradient(to top,{ACC},#B4505A)"></div>' for k in range(48))
    return frame(f"""<div style="position:absolute;inset:0;padding:20px;display:flex;flex-direction:column;gap:16px">
  {card(f'<div style="display:flex;gap:24px;align-items:center">{cover(150,18)}<div style="flex:1;min-width:0">{title_block(32,20)}<div style="margin-top:14px">{prog()}</div></div>{controls(62,80,16)}</div><div style="margin-top:20px;height:150px;display:flex;align-items:flex-end;gap:4px;overflow:hidden">{bars}</div>','flex:1;min-height:0;padding:22px')}
  <div style="height:80px;display:flex;gap:12px">{''.join(f'<div style="flex:1;background:{SURF};border-radius:16px;display:flex;align-items:center;justify-content:center;gap:10px">{aicon(i,40)}<span class="l1" style="font-size:15px">{APPS[i][0]}</span></div>' for i in [1,2,3,4,5])}</div></div>""")


def n16_recientes():  # favoritos + recientes
    fav = "".join(tile(i, 64, 15) for i in [0, 1, 2, 3])
    rec = "".join(f'<div style="flex:1;background:{SURF};border-radius:16px;display:flex;align-items:center;gap:12px;padding:0 14px">{aicon(i,42)}<div style="min-width:0"><div class="l1" style="font-size:16px">{APPS[i][0]}</div><div style="font-size:12px;color:{DIM}">hace {m} min</div></div></div>' for i, m in [(5, 2), (7, 15), (6, 40), (8, 55)])
    return frame(f"""<div style="position:absolute;inset:0;padding:20px;display:flex;gap:16px">
  {card(f'{cover(220,20)}<div style="margin-top:14px;width:100%">{title_block(24,17)}</div><div style="margin-top:12px;width:100%">{prog()}</div><div style="flex:1"></div>{controls(56,72,18)}','width:330px;padding:22px;display:flex;flex-direction:column;align-items:center')}
  <div style="flex:1;display:flex;flex-direction:column;gap:12px">
    <div style="font-size:13px;letter-spacing:2px;color:{DIM}">FAVORITOS</div>
    <div style="flex:1.3;display:grid;grid-template-columns:repeat(4,1fr);gap:12px">{fav}</div>
    <div style="font-size:13px;letter-spacing:2px;color:{DIM}">USADAS HACE POCO</div>
    <div style="flex:1;display:grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:10px">{rec}</div></div></div>""")


def n17_categorias():  # grupos
    grp = lambda t, ids, col: card(f'<div style="font-size:14px;letter-spacing:2px;color:{col};margin-bottom:12px">{t}</div><div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">'
                                   + "".join(f'<div style="height:96px;background:{HIGH};border-radius:14px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:6px;padding:6px">{aicon(i,46)}<div class="l2" style="font-size:13px">{APPS[i][0]}</div></div>' for i in ids)
                                   + '</div>', 'flex:1;padding:16px')
    return frame(f"""<div style="position:absolute;inset:0;padding:18px;display:flex;flex-direction:column;gap:14px">
  <div style="height:80px;background:{HIGH};border-radius:20px;display:flex;align-items:center;gap:16px;padding:0 14px">{cover(56,12)}<div style="flex:1;min-width:0"><div class="l1" style="font-size:19px;font-weight:600">Camino de noche</div><div style="font-size:14px;color:{DIM}">Artista de ejemplo</div></div>{controls(52,62,12)}</div>
  <div style="flex:1;display:flex;gap:14px">{grp('MULTIMEDIA',[0,1,2,6],'#FFB74D')}{grp('CONDUCCIÓN',[4,3,10,7],'#64B5F6')}{grp('HERRAMIENTAS',[5,8,9,7],'#81C784')}</div></div>""")


def n18_volumen():  # volumen y ecualizador rápidos
    eq = "".join(f'<div style="flex:1;display:flex;flex-direction:column;align-items:center;gap:8px"><div style="flex:1;width:10px;border-radius:5px;background:{OUTL};position:relative"><div style="position:absolute;left:-9px;top:{p}%;width:28px;height:28px;border-radius:50%;background:{ACC}"></div></div><span style="font-size:12px;color:{DIM}">{b}</span></div>'
                 for b, p in [("60", 30), ("230", 45), ("910", 55), ("3k", 40), ("14k", 25)])
    return frame(f"""<div style="position:absolute;inset:0;padding:20px;display:flex;gap:16px">
  {card(f'<div style="display:flex;gap:22px;align-items:center">{cover(170,18)}<div style="flex:1;min-width:0">{title_block(28,18)}<div style="margin-top:14px">{prog()}</div></div></div><div style="margin-top:22px">{controls(64,82,30)}</div><div style="margin-top:24px;display:flex;align-items:center;gap:16px">{ico("vol",30,TXT)}<div style="flex:1;height:14px;border-radius:7px;background:{OUTL}"><div style="width:62%;height:14px;border-radius:7px;background:{ACC}"></div></div><span style="font-size:22px;width:40px">18</span></div>','flex:1.5;padding:24px;display:flex;flex-direction:column')}
  <div style="flex:1;display:flex;flex-direction:column;gap:14px">
    {card(f'<div style="display:flex;align-items:center;gap:10px;font-size:14px;letter-spacing:2px;color:{DIM}">{ico("eq",22,DIM)}ECUALIZADOR · ROCK</div><div style="margin-top:12px;height:170px;display:flex">{eq}</div>','padding:16px')}
    <div style="flex:1;display:grid;grid-template-columns:1fr 1fr;gap:12px">{tile(1,48,14)}{tile(3,48,14)}{tile(4,48,14)}<div style="background:{HIGH};border-radius:18px;display:flex;align-items:center;justify-content:center">{ico("apps",34,ACC)}</div></div></div></div>""")


def n19_solo_musica():  # pantalla completa de música, lectura de un vistazo
    return frame(f"""<div style="position:absolute;inset:-80px;background:{ART};filter:blur(90px);opacity:.35"></div>
<div style="position:absolute;inset:0;background:rgba(0,0,0,.55)"></div>
<div style="position:absolute;inset:0;padding:36px 46px;display:flex;flex-direction:column">
  <div style="font-size:16px;letter-spacing:3px;color:{ACC}">AHORA SUENA · 3 / 48</div>
  <div class="l1" style="margin-top:10px;font-size:72px;font-weight:700;line-height:1.05">Camino de noche</div>
  <div style="font-size:34px;color:#C5CBD6">Artista de ejemplo</div>
  <div style="flex:1"></div>
  {prog(34,10,'rgba(255,255,255,.18)')}
  <div style="margin-top:8px;display:flex;justify-content:space-between;font-size:18px;color:#C5CBD6"><span>1:17</span><span>−2:28</span></div>
  <div style="margin-top:22px;display:flex;align-items:center;gap:36px">{btn('prev',90,bg='rgba(255,255,255,.12)')}{btn('pause',116,True)}{btn('next',90,bg='rgba(255,255,255,.12)')}<span style="flex:1"></span>
    <div style="height:72px;padding:0 26px;border-radius:36px;background:rgba(255,255,255,.12);display:flex;align-items:center;gap:12px;font-size:19px">{ico('apps',28,ACC)}Apps</div></div></div>""")


def n20_rueda():  # apps en arco alrededor del botón de reproducción
    import math
    cx, cy, R = 512, 330, 240
    items = ""
    for k, i in enumerate([1, 2, 3, 4, 5, 6, 9]):
        a = math.radians(180 + k * (180 / 6))
        x, y = cx + R * math.cos(a), cy + R * math.sin(a)
        items += (f'<div style="position:absolute;left:{x-46}px;top:{y-46}px;width:92px;display:flex;flex-direction:column;align-items:center;gap:6px">'
                  f'{aicon(i,62)}<div class="l2" style="font-size:13px">{APPS[i][0]}</div></div>')
    return frame(f"""<div style="position:absolute;left:{cx-R}px;top:{cy-R}px;width:{2*R}px;height:{2*R}px;border-radius:50%;border:1px dashed {OUTL}"></div>
  {items}
  <div style="position:absolute;left:{cx-100}px;top:{cy-100}px;width:200px;height:200px;border-radius:50%;background:{ART};display:flex;align-items:center;justify-content:center;box-shadow:0 0 0 10px {SURF}">
    <div style="width:96px;height:96px;border-radius:50%;background:{ACC};display:flex;align-items:center;justify-content:center">{ico('pause',52,BG)}</div></div>
  <div style="position:absolute;left:0;right:0;top:{cy+112}px;text-align:center"><div style="font-size:26px;font-weight:600">Camino de noche</div><div style="font-size:17px;color:{DIM}">Artista de ejemplo</div></div>
  <div style="position:absolute;left:{cx-280}px;top:{cy+110}px">{btn('prev',62)}</div><div style="position:absolute;left:{cx+218}px;top:{cy+110}px">{btn('next',62)}</div>
  <div style="position:absolute;right:22px;bottom:22px;width:72px;height:72px;border-radius:50%;background:{HIGH};display:flex;align-items:center;justify-content:center">{ico('apps',32,ACC)}</div>""")


PROPS = [
    ("01-conduccion", r01_conduccion), ("02-dock-lateral", r02_dock_lateral), ("03-tres-columnas", r03_tres_columnas),
    ("04-portada-de-fondo", r04_portada_fondo), ("05-carrusel", r05_carrusel), ("06-alto-contraste", r06_alto_contraste),
    ("07-botonera", r07_botonera), ("08-alcance-conductor", r08_alcance), ("09-modo-dia", r09_modo_dia), ("10-bento", r10_bento),
    ("11-cola-de-reproduccion", n11_cola), ("12-selector-de-fuente", n12_fuentes), ("13-radio-con-dial", n13_radio),
    ("14-apps-primero", n14_apps_primero), ("15-espectro", n15_espectro), ("16-favoritos-y-recientes", n16_recientes),
    ("17-categorias", n17_categorias), ("18-volumen-y-ecualizador", n18_volumen), ("19-solo-musica", n19_solo_musica),
    ("20-rueda", n20_rueda),
]

if __name__ == "__main__":
    import tempfile
    TMP = pathlib.Path(tempfile.mkdtemp())
    OUT.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(viewport={"width": 1024, "height": 600}, device_scale_factor=1)
        for name, fn in PROPS:
            pg.set_content(fn())
            pg.wait_for_timeout(120)
            pg.screenshot(path=str(TMP / f"propuesta-{name}.png"))
        b.close()
    from PIL import Image, ImageDraw, ImageFont
    W, Hh, G = 1024, 600, 16
    f = ImageFont.truetype('/usr/share/fonts/opentype/inter/Inter-Bold.otf', 26)
    for part, chunk in enumerate([PROPS[:10], PROPS[10:]], 1):
        sheet = Image.new('RGB', (2*W+3*G, 5*Hh+6*G), '#222'); d = ImageDraw.Draw(sheet)
        for k, (name, _) in enumerate(chunk):
            x, y = G+(k % 2)*(W+G), G+(k//2)*(Hh+G)
            sheet.paste(Image.open(TMP / f"propuesta-{name}.png").convert('RGB'), (x, y))
            t = name.replace('-', ' ')
            d.rounded_rectangle((x+10, y+Hh-50, x+24+d.textlength(t, font=f), y+Hh-10), 8, fill='#000'); d.text((x+17, y+Hh-46), t, font=f, fill='white')
        sheet.thumbnail((1400, 2100)); sheet.save(OUT / f"propuestas-{part}-de-2.png", optimize=True)
    print("OK")
