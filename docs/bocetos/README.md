# Bocetos del rediseño (octubre 2026)

Propuestas de interfaz para la pantalla de inicio, dibujadas a la medida real del JCAC10003: la barra del sistema
(66 px) arriba y el área del launcher de 1024×534 debajo. Son renders con datos de ejemplo, no capturas.

- [`propuestas-1-de-2.png`](propuestas-1-de-2.png) y [`propuestas-2-de-2.png`](propuestas-2-de-2.png): 20 propuestas
  (conducción, dock lateral, tres columnas, portada de fondo, carrusel, alto contraste, botonera, alcance del conductor,
  modo día, bento, cola de reproducción, selector de fuente, radio con dial, apps primero, espectro, favoritos y recientes,
  categorías, volumen y ecualizador, solo música y rueda).
- Variaciones del estilo **nocturno**:
  - [`nocturno-1-fiel.png`](nocturno-1-fiel.png)
  - [`nocturno-2-musica-al-centro.png`](nocturno-2-musica-al-centro.png) ← **la elegida e implementada en la 3.1.0**
  - [`nocturno-3-reloj-y-musica.png`](nocturno-3-reloj-y-musica.png)

Se regeneran con `python3 docs/preview/propuestas.py` y `python3 docs/preview/nocturno.py` (requieren `playwright` y `pillow`).
¿Quieres implementar otra? Las piezas (música, dock, panel de apps) son reutilizables; abre un *issue* o un *pull request*.
