#!/usr/bin/env bash
# =====================================================================================
#  launcher-nativo-github.sh — añade la integración con GitHub al proyecto y lo publica en
#  https://github.com/web-gdl/Laucher_8227L_JCAC10003
#
#  Uso (en la carpeta donde está launcher-nativo-src.sh):
#      bash launcher-nativo-src.sh        # crea ./launcher-nativo (si no existe ya)
#      bash launcher-nativo-github.sh     # añade workflow + .gitignore + README y hace git push
#
#  Requiere git con acceso al repositorio (HTTPS con token o SSH). Para no hacer push, ejecuta
#  con la variable NO_PUSH=1.
# =====================================================================================
set -euo pipefail
REPO_URL="${REPO_URL:-https://github.com/web-gdl/Laucher_8227L_JCAC10003.git}"
D=launcher-nativo

if [ ! -d "$D" ]; then
  if [ -f launcher-nativo-src.sh ]; then bash launcher-nativo-src.sh; else
    echo "No existe ./$D ni launcher-nativo-src.sh. Ejecuta primero el autoextraíble."; exit 1; fi
fi
cd "$D"
mkdir -p .github/workflows

# ------------------------------------------------------------------ .github/workflows/build.yml
cat > .github/workflows/build.yml <<'__EOF__'
name: Compilar APK

on:
  push:
    branches: [ main, master ]
    tags: [ 'v*' ]
  pull_request:
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    name: aapt2 + javac + d8 + firma
    runs-on: ubuntu-latest
    steps:
      - name: Código
        uses: actions/checkout@v4

      - name: JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Herramientas (android.jar 27, aapt2, jadx/D8/apksig, claves AOSP)
        run: |
          chmod +x build.sh tools/get-tools.sh
          ./tools/get-tools.sh

      - name: Compilar, empaquetar y firmar
        run: ./build.sh

      - name: Resumen del APK
        run: |
          ls -la out/
          tools/aapt2 dump badging out/launcher-nativo.apk | grep -E "^package|sdkVersion|targetSdk|launchable" || true
          echo "sha256: $(sha256sum out/launcher-nativo.apk | cut -d' ' -f1)"

      - name: Guardar APK como artefacto
        uses: actions/upload-artifact@v4
        with:
          name: launcher-nativo-apk
          path: out/launcher-nativo.apk
          if-no-files-found: error

      - name: Publicar release (solo etiquetas v*)
        if: startsWith(github.ref, 'refs/tags/v')
        uses: softprops/action-gh-release@v2
        with:
          files: out/launcher-nativo.apk
          generate_release_notes: true
__EOF__

# ------------------------------------------------------------------ .gitignore
cat > .gitignore <<'__EOF__'
# salida de compilación
build/
out/

# herramientas descargadas por tools/get-tools.sh (no se versionan)
tools/*.jar
tools/aapt2
tools/aapt2_64
tools/*.zip
tools/*.pk8
tools/*.pem

# varios
*.log
.DS_Store
.idea/
*.iml
__EOF__

# ------------------------------------------------------------------ README.md
cat > README.md <<'__EOF__'
# Launcher nativo para el alps 8227L (Jancar) — v3.0.0-nativo

Sustituto del launcher de fábrica (`com.android.launcher3` 2.0 AllInOne) con interfaz 100 % nativa:
sin motor de temas, sin ZIP cifrados, sin support library, sin bucles de sondeo. Java puro, API 27,
cero dependencias. Conserva la **identidad** del original (paquete, `sharedUserId=android.uid.system`,
`persistent`) y se firma con la **clave de plataforma pública de AOSP**, que es la que usa este equipo,
así que se instala como **actualización** del launcher de fábrica y se revierte con un `adb uninstall`.

## Qué hace (v0.1)

- Reloj grande y fecha (se actualiza con `TIME_TICK`, solo en primer plano).
- Estado Bluetooth (conectado/encendido) y Wi‑Fi (con nivel), por broadcasts y `NetworkCallback`.
- Widget de música conectado a `com.jancar.services` (`IMedia`): fuente, título, artista, portada, progreso,
  anterior / play‑pausa / siguiente. Tocar la tarjeta abre la app de música (configurable con pulsación larga:
  sirve para RadilloPlayerWave); con radio/BT/vídeo abre la app de fábrica correspondiente.
- 8 favoritos (4×2) con iconos reducidos al tamaño de celda y caché `LruCache` de 3 MB; panel "Aplicaciones"
  con todas las apps, ocultar/mostrar, información y desinstalar.
- Barra: Aplicaciones · Apagar pantalla (`ISystem.closeBackLight(1)`) · Ajustes · Diagnóstico.
- Igual que el de fábrica: 6 s tras arrancar pide al servicio reabrir la última app multimedia (`openMemoryApp`),
  y escribe los `Settings.Global` que SystemUI de Jancar consulta (`jancar_sysui_home_enter`, `LauncherActivityView`).
- Pantalla de **Diagnóstico**: heap Java/nativo, PSS, memoria del sistema, estado de los tres servicios
  (media/system/car), ACC, cámara, último evento, registro de los últimos 250 eventos; botones para
  reconectar, forzar GC y guardar el informe (`Android/data/com.android.launcher3/files/launcher-diag.txt`).
- Reconexión a los servicios con `linkToDeath` + broadcast `com.jancar.services.ready` + reintentos 1/2/5/10/30 s.
- Si algo revienta, se escribe `files/crash.txt` con la pila y el registro; **no** se mata el proceso a propósito.

Fuera de alcance por ahora (fase 2): protector de pantalla propio, velocidad GPS, navegación embebida,
canbus, DVR. El equipo ya gestiona la cámara de reversa y el apagado por ACC desde el servicio.

## Estructura

```
AndroidManifest.xml        identidad del launcher de fábrica + permisos de firma
build.sh                   aapt2 → javac → d8 → zip → firma (apksig) → verificación
.github/workflows/         compilación automática en GitHub Actions
res/                       layouts nativos (1024×600), vectores, colores, strings es/en
src/com/android/launcher3/
  LauncherApp              proceso: prefs, repositorio de apps, puente Jancar, openMemoryApp a los 6 s
  HomeActivity             inicio + panel de apps (una sola Activity)
  DiagnosticsActivity      informe de memoria/servicios
  AppsRepository           PackageManager e iconos en un único hilo de fondo; LruCache
  Prefs, AppEntry, Diag, BuildInfo
  jancar/JancarBridge      cliente binder propio de IMedia / ISystem / ICar (códigos del APK de fábrica)
  jancar/MediaState
  ui/ClockController, StatusController, MediaWidget
tools/                     get-tools.sh, Sign.java, Verify.java (+ descargas: android-27.jar, aapt2, jadx-all.jar, claves AOSP)
```

## Compilar en GitHub (sin instalar nada)

En cada *push* a `main`, GitHub Actions descarga las herramientas, ejecuta `build.sh` y deja
`launcher-nativo.apk` como **artefacto** del workflow (pestaña *Actions* → ejecución → *Artifacts*).
Si se publica una etiqueta `v*` (`git tag v0.1.0 && git push --tags`) además crea un **Release** con el APK adjunto.

## Compilar en local

Requisitos: Linux/macOS/WSL con **JDK 17+**, `python3`, `curl`, `unzip`.

```bash
./tools/get-tools.sh      # descarga android.jar (API 27), aapt2 (de apktool), jadx-all (D8+apksig) y las claves AOSP
./build.sh                # genera out/launcher-nativo.apk firmado y verificado
```

`get-tools.sh` descarga desde GitHub: `Sable/android-platforms` (android.jar), `iBotPeaches/Apktool`
(aapt2 para Linux x86‑64; en macOS usa el `aapt2` de tus build-tools), `skylot/jadx` y
`aosp-mirror/platform_build` (test keys). El certificado de la firma debe ser SHA‑256
`C8A2E9BC CF597C2F B6DC66BE E293FC13 F2FC47EC 77BC6B2B 0D52C11F 51192AB8` (el mismo del launcher de fábrica).

## Instalar en el estéreo

```bash
adb devices
adb shell dumpsys package com.android.launcher3 | grep -E "versionCode|codePath"   # fábrica: versionCode=2020
adb install -r launcher-nativo.apk              # actualización: mismo paquete, misma firma, versionCode 2021
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

Si `adb install` falla con `INSTALL_FAILED_UPDATE_INCOMPATIBLE` o `signatures do not match`, el equipo **no**
usa la clave de prueba de AOSP (se comprobó en el APK de fábrica, pero conviene confirmarlo en el aparato):
en ese caso no se puede sustituir el launcher sin la clave real.

### Revertir

```bash
adb uninstall com.android.launcher3     # quita la actualización; vuelve el launcher de fábrica de /system
```
o en el equipo: Ajustes → Aplicaciones → Inicio → "Desinstalar actualizaciones".

Si el nuevo launcher no arranca, el equipo muestra pantalla negra (no hay otra HOME): el `adb uninstall`
sigue funcionando. Conviene tener la depuración USB/ADB por red activada antes de instalar.

## Qué medir en el equipo

```bash
adb shell dumpsys meminfo com.android.launcher3      # antes (fábrica) y después; mirar "TOTAL" y "Graphics"
adb shell dumpsys gfxinfo com.android.launcher3      # janky frames
adb logcat -s LauncherNativo                          # registro del launcher
adb shell cat /sdcard/Android/data/com.android.launcher3/files/launcher-diag.txt
adb shell getprop persist.jancar.customer_id; adb shell cat /jancar/config/ivi-launcher.json
```

## Protocolo binder usado (de los stubs AIDL del APK de fábrica)

| Interfaz (descriptor) | Acción de bind | Códigos |
|---|---|---|
| `com.jancar.services.media.IMedia` | `com.jancar.services.action.media` | registerMediaInfoCallback 5 · unRegister 6 · getActiveMedia 7 · playPause 17 · prev 18 · next 19 · requestMediaInfoAndStateEvent 22 |
| `com.jancar.services.media.IMediaInfoCallback` (nuestro stub) | — | onMediaChange 1 (int tipo, String título, String artista, int w, int h, byte[] píxeles, int idx, int total, bool fav) · onPlayStateChange 2 (tipo, estado 1/2/3, pos_s, dur_s) · onMediaZoneChanged 3 · onCurrentShownMediaTypeChanged 4 |
| `com.jancar.services.system.ISystem` | `com.jancar.services.action.system` | closeBackLight(int) 10 · isOpenBackLight 13 · openMemoryApp 18 · getMemoryAppPackageName 19 |
| `com.jancar.services.car.ICar` | `com.jancar.services.action.car` | registerCallback 3 · unRegisterCallback 4 · getCcdStatus 10 · isAccOn 52 |
| `com.jancar.services.car.ICarCallback` (nuestro stub) | — | onAccChanged(bool) 2 · onCcdChanged(int) 3 · (códigos 1–32 aceptados e ignorados) |

Paquete del servicio: `com.jancar.services`; servicio principal `com.jancar.services.action.main`;
aviso de disponibilidad `com.jancar.services.ready`. Tipos de fuente: 0 música, 1 radio, 2 vídeo, 7 BT, 20 terceros.
__EOF__

chmod +x build.sh tools/get-tools.sh
echo "Archivos de GitHub añadidos en ./$D"

# ------------------------------------------------------------------ git
if [ "${NO_PUSH:-0}" = "1" ]; then echo "NO_PUSH=1: no se hace push."; exit 0; fi
if ! command -v git >/dev/null; then echo "git no está instalado; sube la carpeta manualmente."; exit 0; fi

if [ ! -d .git ]; then git init -b main 2>/dev/null || { git init && git checkout -b main; }; fi
git add -A
if git diff --cached --quiet; then echo "Nada nuevo que confirmar."; else
  git commit -m "Launcher nativo v0.1 para alps 8227L (JCAC10003): UI nativa, cliente binder Jancar, CI en GitHub Actions"
fi
if git remote get-url origin >/dev/null 2>&1; then git remote set-url origin "$REPO_URL"; else git remote add origin "$REPO_URL"; fi
echo "Publicando en $REPO_URL ..."
if ! git push -u origin main; then
  echo "El remoto ya tiene commits (p. ej. un README inicial): integrando historias..."
  git pull --rebase --allow-unrelated-histories origin main || git pull --rebase --allow-unrelated-histories origin master
  git push -u origin main
fi
echo
echo "Listo. GitHub Actions compilará el APK: https://github.com/web-gdl/Laucher_8227L_JCAC10003/actions"
echo "Para publicar un Release con el APK:  git tag v0.1.0 && git push --tags"
