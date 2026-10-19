#!/usr/bin/env python3
"""
Genera docs/screenshots/04-diagnostico.png a 1024x600 (el inicio y el panel de apps: nocturno.py) (la pantalla del 8227L).

IMPORTANTE: no son capturas del equipo. Son un render del diseño: el mismo layout, colores,
medidas (1 dp = 1 px, como en la pantalla de referencia) y vectores que res/ del proyecto,
dibujados en HTML con datos de ejemplo y pasados por Chromium. Si cambias res/layout o
res/values, actualiza este archivo y vuelve a ejecutarlo:

    pip install playwright && python3 docs/preview/render.py
"""
import pathlib
from playwright.sync_api import sync_playwright

OUT = pathlib.Path(__file__).resolve().parent.parent / "screenshots"

C = dict(bg="#0B0D11", surface="#161A21", high="#1F242D", outline="#2A303B",
         text="#ECEFF4", dim="#8A94A6", accent="#FFB74D")

# Vectores de res/drawable (viewport 24x24)
P = dict(
    apps="M4,8h4V4H4v4zm6,12h4v-4h-4v4zm-6,0h4v-4H4v4zm0,-6h4v-4H4v4zm6,0h4v-4h-4v4zm6,-10v4h4V4h-4zm-6,4h4V4h-4v4zm6,6h4v-4h-4v4zm0,6h4v-4h-4v4z",
    power="M13,3h-2v10h2V3zm4.83,2.17l-1.42,1.42C17.99,7.86 19,9.81 19,12c0,3.87 -3.13,7 -7,7s-7,-3.13 -7,-7c0,-2.19 1.01,-4.14 2.58,-5.42L6.17,5.17C4.23,6.82 3,9.26 3,12c0,4.97 4.03,9 9,9s9,-4.03 9,-9c0,-2.74 -1.23,-5.18 -3.17,-6.83z",
    settings="M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
    info="M11,17h2v-6h-2v6zm1,-15C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zm0,18c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM11,9h2V7h-2v2z",
    bt="M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zm1.88,10.46L13,18.17v-3.76l1.88,1.88z",
    wifi="M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zm8,8l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zm-4,-4l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z",
    play="M8,5v14l11,-7z",
    pause="M6,19h4V5H6v14zm8,-14v14h4V5h-4z",
    next="M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z",
    prev="M6,6h2v12H6zm3.5,6l8.5,6V6z",
    note="M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z",
    add="M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
    radio="M3.24,6.15C2.51,6.43 2,7.17 2,8v12c0,1.1 0.89,2 2,2h16c1.11,0 2,-0.9 2,-2V8c0,-1.11 -0.89,-2 -2,-2H8.3l8.26,-3.34L15.88,1 3.24,6.15zM7,20c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zm13,-8h-2v-2h-2v2H4V8h16v4z",
    back="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z",
    eye="M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zm0,-8c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
    video="M17,10.5V7c0,-0.55 -0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h12c0.55,0 1,-0.45 1,-1v-3.5l4,4v-11l-4,4z",
    phone="M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z",
    nav="M12,2L4.5,20.29l0.71,0.71L12,18l6.79,3 0.71,-0.71z",
    folder="M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z",
    cam="M12,12m-3.2,0a3.2,3.2 0 1,0 6.4,0a3.2,3.2 0 1,0 -6.4,0M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zm3,15c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z",
)


def ico(name, size, color):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" style="flex:none;display:block">'
            f'<path d="{P[name]}" fill="{color}"/></svg>')


# Aplicaciones de ejemplo (no son los iconos reales del equipo)
APPS = [("Música", "note", "#EF6C00"), ("Radio", "radio", "#00897B"), ("Bluetooth", "bt", "#1E88E5"),
        ("Vídeo", "video", "#8E24AA"), ("Ajustes", "settings", "#546E7A"), ("Teléfono", "phone", "#43A047"),
        ("Navegación", "nav", "#3949AB"), ("Cámara", "cam", "#6D4C41"), ("Archivos", "folder", "#F9A825")]


def app_icon(i, size):
    _, g, col = APPS[i]
    return (f'<div style="width:{size}px;height:{size}px;border-radius:{size*0.22}px;background:{col};'
            f'display:flex;align-items:center;justify-content:center">{ico(g, size*0.58, "#fff")}</div>')


CSS = f"""
*{{box-sizing:border-box;margin:0;padding:0}}
html,body{{width:1024px;height:600px;overflow:hidden;background:{C['bg']};
  font-family:'Inter','Roboto',sans-serif;color:{C['text']}}}
.card{{background:{C['surface']};border:1px solid {C['outline']};border-radius:18px}}
.tile{{background:{C['high']};border-radius:16px}}
.round{{border-radius:50%;background:{C['high']};display:flex;align-items:center;justify-content:center}}
"""


def page(body):
    return f"<!doctype html><meta charset=utf-8><style>{CSS}</style>{body}"


def home(playing=True):
    # Columna izquierda 40 % / derecha 60 % con 14 de separación; padding 20
    if playing:
        title, artist, src = "Camino de noche", "Artista de ejemplo", "MÚSICA"
        prog, tm, pbtn = 32, "1:12 / 3:45", "pause"
        cover = (f'<div style="width:112px;height:112px;border-radius:14px;background:{C["high"]};'
                 f'display:flex;align-items:center;justify-content:center;flex:none">{ico("note", 56, C["dim"])}</div>')
    else:
        title, artist, src = "Sin reproducción", "Toca para abrir la música", "MÚSICA"
        prog, tm, pbtn = 0, "", "play"
        cover = (f'<div style="width:112px;height:112px;border-radius:14px;background:{C["high"]};'
                 f'display:flex;align-items:center;justify-content:center;flex:none">{ico("note", 56, C["dim"])}</div>')
    favs = ""
    for i in range(8):
        if i < 5:
            favs += (f'<div class="tile" style="display:flex;flex-direction:column;align-items:center;justify-content:center;'
                     f'padding:16px 8px 14px">{app_icon(i, 72)}<div style="margin-top:10px;font-size:15px">{APPS[i][0]}</div></div>')
        else:
            favs += (f'<div style="border:1px dashed {C["outline"]};border-radius:16px;display:flex;flex-direction:column;'
                     f'align-items:center;justify-content:center;padding:16px 8px 14px">{ico("add", 72, C["dim"])}'
                     f'<div style="margin-top:10px;font-size:15px;color:{C["dim"]}">Añadir</div></div>')

    def bar(icon, label, col):
        return (f'<div style="flex:1;background:{C["surface"]};border-radius:16px;display:flex;align-items:center;'
                f'justify-content:center">{ico(icon, 28, col)}<span style="margin-left:10px;font-size:15px;white-space:nowrap;letter-spacing:-0.3px">{label}</span></div>')

    return page(f"""
<div style="position:absolute;inset:0;padding:20px;display:flex;gap:14px">
 <div style="width:388px;display:flex;flex-direction:column;gap:14px">
  <div class="card" style="flex:46;padding:12px 24px 14px;display:flex;flex-direction:column">
    <div style="flex:1;display:flex;align-items:center;font-size:104px;font-weight:200;line-height:1;letter-spacing:-2px">8:42</div>
    <div style="font-size:22px;color:{C['dim']}">Sábado, 17 de octubre</div>
    <div style="margin-top:10px;display:flex;align-items:center;height:26px">
      {ico('bt', 26, C['accent'])}<span style="width:14px"></span>{ico('wifi', 26, C['accent'])}
      <span style="margin-left:14px;font-size:14px;color:{C['dim']}">Bluetooth conectado</span></div>
  </div>
  <div class="card" style="flex:54;padding:18px;display:flex;flex-direction:column">
    <div style="flex:1;display:flex;min-height:0">
      {cover}
      <div style="margin-left:18px;flex:1;display:flex;flex-direction:column;justify-content:center;min-width:0">
        <div style="font-size:13px;letter-spacing:1px;color:{C['accent']}">{src}</div>
        <div style="margin-top:4px;font-size:22px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{title}</div>
        <div style="margin-top:2px;font-size:17px;color:{C['dim']};white-space:nowrap">{artist}</div>
        <div style="margin-top:12px;height:6px;border-radius:3px;background:{C['outline']};visibility:{'visible' if playing else 'hidden'}">
          <div style="width:{prog}%;height:6px;border-radius:3px;background:{C['accent']}"></div></div>
        <div style="margin-top:4px;font-size:12px;color:{C['dim']};height:15px">{tm}</div>
      </div>
    </div>
    <div style="margin-top:14px;display:flex;align-items:center;justify-content:center">
      <div class="round" style="width:56px;height:56px">{ico('prev', 30, C['text'])}</div>
      <div class="round" style="width:68px;height:68px;margin:0 22px;background:{C['accent']}">{ico(pbtn, 36, C['bg'])}</div>
      <div class="round" style="width:56px;height:56px">{ico('next', 30, C['text'])}</div>
    </div>
  </div>
 </div>
 <div style="flex:1;display:flex;flex-direction:column;gap:14px">
  <div style="flex:1;display:grid;grid-template-columns:repeat(4,1fr);grid-template-rows:1fr 1fr;gap:14px">{favs}</div>
  <div style="height:64px;display:flex;gap:14px">
    {bar('apps', 'Aplicaciones', C['accent'])}{bar('power', 'Apagar pantalla', C['text'])}
    {bar('settings', 'Ajustes', C['text'])}{bar('info', 'Diagnóstico', C['text'])}
  </div>
 </div>
</div>""")


def apps_panel():
    cells = ""
    for i in range(9):
        cells += (f'<div class="tile" style="display:flex;flex-direction:column;align-items:center;padding:14px 6px 12px;'
                  f'height:130px">{app_icon(i, 64)}<div style="margin-top:8px;font-size:14px">{APPS[i][0]}</div></div>')
    return page(f"""
<div style="position:absolute;inset:0;padding:20px;display:flex;flex-direction:column">
  <div style="height:56px;display:flex;align-items:center">
    <div class="round" style="width:56px;height:56px">{ico('back', 28, C['text'])}</div>
    <div style="margin-left:18px;flex:1;font-size:24px;font-weight:500">Aplicaciones</div>
    <div style="margin-right:18px;font-size:15px;color:{C['dim']}">29 · 2 ocultas</div>
    <div class="round" style="width:56px;height:56px">{ico('eye', 28, C['dim'])}</div>
  </div>
  <div style="margin-top:14px;display:grid;grid-template-columns:repeat(6,1fr);gap:14px">{cells}</div>
</div>""")


def diag():
    txt = """Launcher nativo 3.0.0-nativo (2021) · variante prueba com.webgdl.launcher8227
Proceso activo: 1284 s

== Memoria ==
Heap Java: 1.7 MB usados / 5.9 MB reservados (máx 192.0 MB)
Heap nativo: 7.8 MB
PSS total del proceso: 26.1 MB
Sistema: 414 MB libres de 1024 MB (umbral 96 MB)
Caché de iconos: 8 bitmaps, 162 KB
Apps lanzables: 29 (escaneos: 1)

== Servicios com.jancar.services ==
media: conectado (conexiones=1, intento=0, última=1283 s)
system: conectado (conexiones=1, intento=0, última=1283 s)
car: conectado (conexiones=1, intento=0, última=1283 s)
ACC: ON · cámara reversa: 0
Media: type=20 state=3 title='Camino de noche' artist='Artista de ejemplo'
Sesiones multimedia: activa · 2 sesiones vistas, 5 cambios

== Registro ==
08:21:14 bridge: media conectado
08:21:14 sesiones: fuente com.ejemplo.player estado 3"""
    btn = lambda t, a=False: (f'<div style="height:44px;padding:0 18px;margin-left:10px;border-radius:16px;display:flex;align-items:center;'
                              f'font-size:14px;background:{C["accent"] if a else C["surface"]};color:{C["bg"] if a else C["text"]}">{t}</div>')
    return page(f"""
<div style="position:absolute;inset:0;padding:20px;display:flex;flex-direction:column">
  <div style="height:56px;display:flex;align-items:center">
    <div class="round" style="width:56px;height:56px">{ico('back', 28, C['text'])}</div>
    <div style="margin-left:18px;flex:1;font-size:24px;font-weight:500">Diagnóstico del launcher</div>
    {btn('Actualizar')}{btn('Reconectar servicios')}{btn('Forzar GC')}{btn('Guardar informe', True)}
  </div>
  <div class="card" style="margin-top:14px;flex:1;padding:16px;font-family:'DejaVu Sans Mono',monospace;font-size:13px;
     line-height:1.45;white-space:pre;overflow:hidden">{txt}</div>
</div>""")


SHOTS = {
    # 01-03 los genera nocturno.py (inicio actual); aquí queda la pantalla de diagnóstico
    "04-diagnostico": diag(),
}

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(viewport={"width": 1024, "height": 600}, device_scale_factor=1)
        for name, html in SHOTS.items():
            pg.set_content(html)
            pg.wait_for_timeout(150)
            pg.screenshot(path=str(OUT / f"{name}.png"))
            print("OK", OUT / f"{name}.png")
        b.close()
