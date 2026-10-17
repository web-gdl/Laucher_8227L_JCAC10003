#!/usr/bin/env bash
# Compila, empaqueta y firma el launcher nativo sin Gradle ni Android Studio.
#
#   ./build.sh                       → variante "reemplazo" (com.android.launcher3, uid system, persistente)
#   ./build.sh --variant prueba      → variante "prueba" (com.webgdl.launcher8227, app normal con icono en el cajón)
#   ./build.sh --variant ambas       → las dos
#
# Requisitos (carpeta tools/, los descarga tools/get-tools.sh): android-27.jar, aapt2,
# jadx-1.5.3-all.jar (D8 + apksig), platform.pk8 y platform.x509.pem (test keys de AOSP). JDK 17+.
set -euo pipefail
cd "$(dirname "$0")"
T=tools; OUT=out
JADX=$T/jadx-1.5.3-all.jar
AJAR=$T/android-27.jar
TEST_PACKAGE=com.webgdl.launcher8227
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dfile.encoding=UTF-8"

VARIANT=reemplazo
while [ $# -gt 0 ]; do
  case "$1" in
    --variant) VARIANT="$2"; shift 2 ;;
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    *) echo "Opción desconocida: $1"; exit 2 ;;
  esac
done

for f in "$JADX" "$AJAR" "$T/aapt2" "$T/platform.pk8" "$T/platform.x509.pem"; do
  [ -f "$f" ] || { echo "Falta $f — ejecuta ./tools/get-tools.sh"; exit 1; }
done

# ----------------------------------------------------------------------------------------- una variante
build_one() {
  local variant=$1
  local B="build/$variant" apk manifest link_extra=""
  case "$variant" in
    reemplazo) apk="$OUT/launcher-nativo.apk" ;;
    prueba)    apk="$OUT/launcher-prueba.apk" ;;
    *) echo "Variante desconocida: $variant (reemplazo | prueba | ambas)"; exit 2 ;;
  esac
  echo "=== Variante: $variant → $apk"
  rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/dex" "$B/tools" "$OUT"

  manifest=AndroidManifest.xml
  if [ "$variant" = prueba ]; then
    # App normal: sin uid system ni persistente, con icono en el cajón y nombre distinto.
    # El paquete se renombra en aapt2 link; el código detecta la variante en tiempo de ejecución (BuildInfo).
    manifest="$B/AndroidManifest.xml"
    python3 - AndroidManifest.xml "$manifest" <<'EOF'
import re, sys
src, dst = sys.argv[1:3]
m = open(src, encoding='utf-8').read()
m = re.sub(r'\n[ \t]*android:sharedUserId="[^"]*"', '', m)
m = re.sub(r'\n[ \t]*android:persistent="[^"]*"', '', m)
m = m.replace('@string/app_name"', '@string/app_name_test"')
home = '<category android:name="android.intent.category.HOME" />'
assert home in m, 'no se encontró la categoría HOME en el manifiesto'
m = m.replace(home, home + '\n                <category android:name="android.intent.category.LAUNCHER" />')
assert 'android:sharedUserId=' not in m and 'android:persistent=' not in m
open(dst, 'w', encoding='utf-8').write(m)
EOF
    link_extra="--rename-manifest-package $TEST_PACKAGE"
  fi

  echo "[1/6] aapt2 compile"
  "$T/aapt2" compile --dir res -o "$B/res.zip"

  echo "[2/6] aapt2 link"
  # shellcheck disable=SC2086
  "$T/aapt2" link -o "$B/base.apk" -I "$AJAR" --manifest "$manifest" \
    --java "$B/gen" --min-sdk-version 26 --target-sdk-version 27 \
    --no-auto-version --no-version-vectors $link_extra "$B/res.zip"

  echo "[3/6] javac"
  find src "$B/gen" -name '*.java' > "$B/sources.txt"
  javac --release 8 -encoding UTF-8 -Xlint:-options -proc:none \
    -cp "$AJAR" -d "$B/classes" @"$B/sources.txt" 2>&1 | grep -v "^Picked up" || true
  [ -f "$B/classes/com/android/launcher3/HomeActivity.class" ] || { echo "javac falló"; exit 1; }

  echo "[4/6] d8"
  find "$B/classes" -name '*.class' > "$B/classes.txt"
  java -cp "$JADX" com.android.tools.r8.D8 --release --min-api 26 --lib "$AJAR" \
    --output "$B/dex" @"$B/classes.txt" 2>&1 | grep -v "^Picked up" || true
  [ -f "$B/dex/classes.dex" ] || { echo "d8 falló"; exit 1; }

  echo "[5/6] empaquetar"
  python3 - "$B/base.apk" "$B/dex/classes.dex" "$B/unsigned.apk" <<'EOF'
import sys, zipfile
base, dex, out = sys.argv[1:4]
with zipfile.ZipFile(base) as zin, zipfile.ZipFile(out, 'w') as zout:
    for info in zin.infolist():
        data = zin.read(info.filename)
        # resources.arsc debe ir sin comprimir (lo exige el framework); el resto, deflate
        comp = zipfile.ZIP_STORED if info.filename == 'resources.arsc' else zipfile.ZIP_DEFLATED
        zi = zipfile.ZipInfo(info.filename, date_time=(2009, 1, 1, 0, 0, 0)); zi.compress_type = comp
        zout.writestr(zi, data)
    zi = zipfile.ZipInfo('classes.dex', date_time=(2009, 1, 1, 0, 0, 0)); zi.compress_type = zipfile.ZIP_DEFLATED
    zout.writestr(zi, open(dex, 'rb').read())
EOF

  echo "[6/6] firmar con la clave de plataforma"
  javac -proc:none -nowarn -Xlint:none -cp "$JADX" -d "$B/tools" "$T/Sign.java" "$T/Verify.java" 2>&1 | grep -v "^Picked up\|^Note:" || true
  java -cp "$JADX:$B/tools" Sign "$B/unsigned.apk" "$apk" "$T/platform.pk8" "$T/platform.x509.pem" 2>&1 | grep -v "^Picked up"
  java -cp "$JADX:$B/tools" Verify "$apk" 2>&1 | grep -v "^Picked up"
  ls -la "$apk"
  "$T/aapt2" dump badging "$apk" | grep -E "^package|sdkVersion|targetSdk|launchable|category" || true
  echo "OK: $apk"
}

case "$VARIANT" in
  ambas) build_one reemplazo; echo; build_one prueba ;;
  *) build_one "$VARIANT" ;;
esac
