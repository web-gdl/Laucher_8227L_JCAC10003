#!/usr/bin/env bash
# Descarga las herramientas necesarias para build.sh en esta carpeta (tools/).
set -euo pipefail
cd "$(dirname "$0")"
JADX_VER=1.5.3
APKTOOL_VER=2.12.0

echo "- android.jar (API 27)"
[ -f android-27.jar ] || curl -fL -o android-27.jar \
  "https://raw.githubusercontent.com/Sable/android-platforms/master/android-27/android.jar"

echo "- jadx $JADX_VER (incluye D8/R8 y apksig)"
if [ ! -f "jadx-$JADX_VER-all.jar" ]; then
  curl -fL -o jadx.zip "https://github.com/skylot/jadx/releases/download/v$JADX_VER/jadx-$JADX_VER.zip"
  unzip -o -j jadx.zip "lib/jadx-$JADX_VER-all.jar" -d . && rm -f jadx.zip
fi

echo "- aapt2 (de apktool $APKTOOL_VER, binario Linux x86-64)"
if [ ! -f aapt2 ]; then
  curl -fL -o apktool.jar "https://github.com/iBotPeaches/Apktool/releases/download/v$APKTOOL_VER/apktool_$APKTOOL_VER.jar"
  unzip -o -j apktool.jar prebuilt/linux/aapt2_64 -d . && mv aapt2_64 aapt2 && chmod +x aapt2 && rm -f apktool.jar
  echo "  (en macOS: copia el aapt2 de \$ANDROID_HOME/build-tools/<ver>/ aquí)"
fi

echo "- claves de plataforma de AOSP (test keys)"
for f in platform.pk8 platform.x509.pem; do
  [ -f "$f" ] || curl -fL -o "$f" "https://raw.githubusercontent.com/aosp-mirror/platform_build/android-8.1.0_r1/target/product/security/$f"
done
openssl x509 -in platform.x509.pem -noout -fingerprint -sha256 2>/dev/null || true
echo "Listo. Ahora: ./build.sh"
