#!/usr/bin/env python3
"""Inicio «nocturno» (la variación 2 es la implementada en res/layout/activity_home.xml) y sus variaciones.

Genera docs/screenshots/01..03 (render del diseño, no capturas del equipo) y docs/bocetos/nocturno-*.png.
    pip install playwright pillow && python3 docs/preview/nocturno.py
"""
import pathlib, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from propuestas import frame, ico, aicon, cover, btn, prog, APPS, ACC, DIM, TXT, OUTL, ART  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

DOCS = pathlib.Path(__file__).resolve().parent.parent
OUT = DOCS / "bocetos"
SHOTS = DOCS / "screenshots"
INK = "#D9DEE7"      # blanco suave, no deslumbra
PILL = "#0E1117"; PILL_B = "#1C212A"


def ring(icon, label, s=76, accent=False, color=None):
    col = ACC if accent else (color or DIM)
    return (f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px;width:{s+20}px">'
            f'<div style="width:{s}px;height:{s}px;border-radius:50%;border:1px solid {OUTL};display:flex;align-items:center;justify-content:center">{ico(icon, s*.44, col)}</div>'
            f'<div style="font-size:13px;color:{DIM};text-align:center;line-height:1.15;height:30px;overflow:hidden">{label}</div></div>')


DOCK = [("note", "Música"), ("radio", "Radio"), ("bt", "BT música"), ("phone", "Llamada BT"), ("nav", "Navegación"), ("folder", "Archivos")]


def pill(w=700, h=96, cov=68):
    return (f'<div style="width:{w}px;height:{h}px;border-radius:{h/2}px;background:{PILL};border:1px solid {PILL_B};display:flex;align-items:center;padding:0 14px;gap:16px">'
            f'{cover(cov, cov/2)}<div style="flex:1;min-width:0"><div style="font-size:20px;color:{INK};white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Camino de noche</div>'
            f'<div style="font-size:15px;color:{DIM}">Artista de ejemplo</div><div style="margin-top:8px">{prog(34, 3)}</div></div>'
            f'{btn("prev", 56)}{btn("pause", 68, True)}{btn("next", 56)}</div>')


# V1 · Fiel al boceto: reloj grande (estilo mesa de noche), píldora de música y anillos con nombre
def v1():
    dock = "".join(ring(i, t) for i, t in DOCK) + ring("apps", "Todas", accent=True)
    return frame(f"""<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;padding:14px 40px 16px">
  <div style="font-size:140px;font-weight:200;line-height:1;color:{INK};letter-spacing:-4px">8:10<span style="font-size:40px;letter-spacing:0;margin-left:10px;color:#8A94A6">p. m.</span></div>
  <div style="margin-top:4px;font-size:21px;color:{DIM};letter-spacing:1px">domingo 18 de octubre</div>
  <div style="flex:1"></div>
  {pill()}
  <div style="margin-top:16px;display:flex;gap:14px">{dock}</div>
</div>""", "#000")


def ring_app(i):
    return (f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px;width:96px">'
            f'<div style="width:76px;height:76px;border-radius:50%;border:1px solid {OUTL};display:flex;align-items:center;justify-content:center;opacity:.9">{aicon(i, 44)}</div>'
            f'<div style="font-size:13px;color:{DIM};text-align:center;line-height:1.15;height:30px;overflow:hidden">{APPS[i][0]}</div></div>')


# V2 · La música en el centro: sin reloj duplicado; título grande y la portada como halo
def v2(real=False):
    if real:
        dock = "".join(ring_app(i) for i in [0, 1, 2, 3, 4, 5]) + ring("apps", "Todas", accent=True)
    else:
        dock = "".join(ring(i, t) for i, t in DOCK) + ring("apps", "Todas", accent=True)
    if real:  # como el launcher 3.2: colores de la portada a toda la pantalla, difuminados y oscurecidos
        bg = (f'<div style="position:absolute;inset:-80px;background:{ART};filter:blur(70px) saturate(1.2) brightness(.5)"></div>'
              '<div style="position:absolute;left:0;right:0;bottom:0;height:220px;background:linear-gradient(transparent,rgba(0,0,0,.7))"></div>')
    else:
        bg = f'<div style="position:absolute;left:50%;top:-140px;width:620px;height:620px;margin-left:-310px;border-radius:50%;background:{ART};filter:blur(110px);opacity:.22"></div>'
    return frame(f"""{bg}
<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;padding:26px 40px 16px">
  <div style="display:flex;align-items:center;gap:30px">
    {cover(170, 85)}
    <div style="width:430px">
      <div style="font-size:14px;letter-spacing:3px;color:{ACC}">AHORA SUENA · USB</div>
      <div style="margin-top:6px;font-size:40px;font-weight:600;color:{INK};line-height:1.1">Camino de noche</div>
      <div style="font-size:22px;color:{DIM}">Artista de ejemplo</div>
      <div style="margin-top:16px">{prog(34, 4)}</div>
      <div style="margin-top:6px;display:flex;justify-content:space-between;font-size:14px;color:{DIM}"><span>1:17</span><span>3:45</span></div>
    </div>
  </div>
  <div style="margin-top:22px;display:flex;align-items:center;gap:34px">{btn('prev', 72)}{btn('pause', 92, True)}{btn('next', 72)}</div>
  <div style="flex:1"></div>
  <div style="display:flex;gap:14px">{dock}</div>
</div>""", "#000")


def v2_idle():
    html = v2(True)
    html = html.replace(f'<div style="margin-top:16px">{prog(34, 4)}</div>', '<div style="margin-top:16px;height:4px"></div>')
    html = html.replace("<span>1:17</span><span>3:45</span>", "<span>&nbsp;</span>")
    html = html.replace("AHORA SUENA · USB", "MÚSICA").replace("Camino de noche", "Sin reproducción").replace("Artista de ejemplo", "Toca para abrir la música")
    # sin portada todavía: fondo negro (diseño base) y portada gris
    html = html.replace(f"background:{ART};filter:blur(70px) saturate(1.2) brightness(.5)", "background:#000").replace(f"background:{ART}", "background:#1F242D").replace(ico('pause', 46.0, '#0B0D11'), ico('play', 46.0, '#0B0D11'))
    return html


def apps_panel():
    from propuestas import APPS as A, aicon as ai, HIGH as HI, SURF as SU
    head = "".join(f'<div style="width:56px;height:56px;border-radius:50%;background:{SU};display:flex;align-items:center;justify-content:center;margin-left:12px">{ico(n, 26, TXT)}</div>' for n in ["power", "settings", "info"])
    cells = "".join(f'<div style="border-radius:16px;display:flex;flex-direction:column;align-items:center;padding:14px 6px 10px;height:138px">{ai(i,64)}'
                    f'<div class="l2" style="margin-top:8px;font-size:14px">{A[i][0]}</div></div>' for i in range(11))
    return frame(f"""<div style="position:absolute;inset:0;padding:20px;display:flex;flex-direction:column">
  <div style="height:56px;display:flex;align-items:center">
    <div style="width:56px;height:56px;border-radius:50%;background:{SU};display:flex;align-items:center;justify-content:center">{ico('back', 28, TXT)}</div>
    <div style="margin-left:18px;font-size:24px;font-weight:500;color:{INK}">Aplicaciones</div>
    <div style="margin-left:14px;flex:1;font-size:15px;color:{DIM}">29 · 2 ocultas</div>
    <div style="width:56px;height:56px;border-radius:50%;background:{SU};display:flex;align-items:center;justify-content:center">{ico('eye', 26, DIM)}</div>{head}
  </div>
  <div style="margin-top:14px;display:grid;grid-template-columns:repeat(6,1fr);gap:14px">{cells}</div>
</div>""", "#000")


# V3 · Reloj a la izquierda + música a la derecha; dock con el color de cada app atenuado
def v3():
    dock = "".join(
        f'<div style="display:flex;flex-direction:column;align-items:center;gap:8px;width:96px">'
        f'<div style="width:76px;height:76px;border-radius:50%;background:{APPS[i][2]}26;border:1px solid {APPS[i][2]}55;display:flex;align-items:center;justify-content:center">{ico(APPS[i][1], 34, APPS[i][2])}</div>'
        f'<div style="font-size:13px;color:{DIM};text-align:center;line-height:1.15;height:30px;overflow:hidden">{APPS[i][0]}</div></div>' for i in [0, 1, 2, 3, 4, 5])
    dock += ring("apps", "Todas", accent=True)
    return frame(f"""<div style="position:absolute;inset:0;padding:26px 40px 16px;display:flex;flex-direction:column">
  <div style="flex:1;display:flex;align-items:center;gap:40px">
    <div style="flex:none">
      <div style="font-size:120px;font-weight:200;line-height:1;color:{INK};letter-spacing:-3px">8:10<span style="font-size:34px;letter-spacing:0;margin-left:8px;color:#8A94A6">p. m.</span></div>
      <div style="margin-top:6px;font-size:20px;color:{DIM}">domingo 18 de octubre</div>
    </div>
    <div style="width:1px;height:220px;background:{PILL_B}"></div>
    <div style="flex:1;min-width:0;display:flex;flex-direction:column;gap:14px">
      <div style="display:flex;align-items:center;gap:18px">{cover(120, 20)}
        <div style="min-width:0"><div style="font-size:13px;letter-spacing:3px;color:{ACC}">MÚSICA</div>
        <div style="font-size:26px;color:{INK};font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">Camino de noche</div>
        <div style="font-size:17px;color:{DIM}">Artista de ejemplo</div></div></div>
      {prog(34, 4)}
      <div style="display:flex;align-items:center;gap:22px">{btn('prev', 66)}{btn('pause', 84, True)}{btn('next', 66)}</div>
    </div>
  </div>
  <div style="display:flex;justify-content:center;gap:14px">{dock}</div>
</div>""", "#000")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    SHOTS.mkdir(parents=True, exist_ok=True)
    jobs = [(OUT / "nocturno-1-fiel.png", v1), (OUT / "nocturno-2-musica-al-centro.png", v2), (OUT / "nocturno-3-reloj-y-musica.png", v3),
            (SHOTS / "01-inicio-reproduciendo.png", lambda: v2(True)), (SHOTS / "02-inicio-reposo.png", v2_idle), (SHOTS / "03-todas-las-apps.png", apps_panel)]
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(viewport={"width": 1024, "height": 600}, device_scale_factor=1)
        for path, fn in jobs:
            pg.set_content(fn()); pg.wait_for_timeout(150)
            pg.screenshot(path=str(path))
        b.close()
    print("OK")
