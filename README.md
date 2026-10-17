# Launcher nativo — autoestéreo alps 8227L (Jancar JCAC10003)

Launcher de interfaz 100 % nativa (sin skins ni temas de fábrica) pensado para un equipo con 1 GB de RAM:
una sola Activity, caché de iconos acotada, sin sondeos ni temporizadores en segundo plano.

## Compilar

```
./tools/get-tools.sh              # android.jar 27, aapt2, jadx/D8/apksig y claves de plataforma AOSP
./build.sh --variant prueba       # out/launcher-prueba.apk
./build.sh --variant reemplazo    # out/launcher-nativo.apk
./build.sh --variant ambas
```

En GitHub, cada push a `main` compila las dos y las deja como artefactos del workflow «Compilar APK».

## Variantes

| | prueba | reemplazo |
|---|---|---|
| Paquete | `com.webgdl.launcher8227` | `com.android.launcher3` |
| Convive con el launcher de fábrica | sí (icono «Inicio (prueba)» en el cajón; se elige como inicio al pulsar HOME) | no: lo actualiza |
| uid system / persistente | no | sí |
| Pide la «app de memoria» 6 s tras arrancar | no | sí |

Empieza siempre por la de **prueba**: se instala como una app normal y se desinstala sin tocar nada.

## Qué hace

- Reloj, tarjeta de música (título, artista, portada, progreso, anterior/play/siguiente), 8 favoritos y panel de todas las apps.
- Música: lee el servicio `com.jancar.services` (fuentes de fábrica) y, para apps de terceros (tipo 20), completa
  paquete, estado y posición con la sesión multimedia de Android (`SessionProbe`; necesita la firma de plataforma).
- Pantalla de **Diagnóstico**: memoria, enlaces a los servicios, ACC (servicio, broadcast y `Settings.Global`), sesiones y registro.
