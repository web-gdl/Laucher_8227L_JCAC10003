#!/usr/bin/env bash
# =====================================================================================
#  launcher-nativo-src.sh — paquete autoextraíble del proyecto "Launcher nativo"
#  para el autoestéreo alps 8227L (Jancar). Ejecuta:  bash launcher-nativo-src.sh
#  Crea ./launcher-nativo/ con todo el código. Luego:  cd launcher-nativo && ./tools/get-tools.sh && ./build.sh
# =====================================================================================
set -euo pipefail
D=launcher-nativo
mkdir -p "$D/res/values" "$D/res/values-en" "$D/res/layout" "$D/res/drawable" \
         "$D/src/com/android/launcher3/jancar" "$D/src/com/android/launcher3/ui" "$D/tools"

# ------------------------------------------------------------------ AndroidManifest.xml
cat > "$D/AndroidManifest.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<!--
  Launcher nativo para el autoestéreo alps 8227L (Jancar).
  Conserva la identidad del launcher de fábrica (paquete, sharedUserId, persistent)
  para heredar sus privilegios y poder instalarse como actualización.
-->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.android.launcher3"
    android:sharedUserId="android.uid.system"
    android:versionCode="2021"
    android:versionName="3.0.0-nativo">

    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="27" />

    <!-- Para los Settings.Global que SystemUI de Jancar consulta -->
    <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
    <!-- Estado Bluetooth / Wi-Fi en la barra de estado propia -->
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <!-- Desinstalar apps desde el menú contextual -->
    <uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />
    <!-- Reutilizado del launcher original: el servicio comprueba la actividad superior -->
    <uses-permission android:name="android.permission.GET_TASKS" />

    <application
        android:name=".LauncherApp"
        android:label="@string/app_name"
        android:icon="@drawable/ic_launcher"
        android:theme="@style/Theme.Launcher"
        android:persistent="true"
        android:allowBackup="false"
        android:hardwareAccelerated="true"
        android:supportsRtl="false"
        android:extractNativeLibs="false">

        <activity
            android:name=".HomeActivity"
            android:label="@string/app_name"
            android:launchMode="singleTask"
            android:clearTaskOnLaunch="true"
            android:stateNotNeeded="true"
            android:excludeFromRecents="true"
            android:resumeWhilePausing="true"
            android:screenOrientation="nosensor"
            android:configChanges="orientation|screenSize|keyboardHidden|uiMode|screenLayout"
            android:windowSoftInputMode="adjustPan"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <activity
            android:name=".DiagnosticsActivity"
            android:label="@string/diag_title"
            android:excludeFromRecents="true"
            android:screenOrientation="nosensor"
            android:exported="false" />
    </application>
</manifest>
__EOF__

# ------------------------------------------------------------------ build.sh
cat > "$D/build.sh" <<'__EOF__'
#!/usr/bin/env bash
# Compila, empaqueta y firma el launcher nativo sin Gradle ni Android Studio.
# Requisitos (carpeta tools/): android-27.jar, aapt2, jadx-1.5.3-all.jar (D8 + apksig),
# platform.pk8 y platform.x509.pem (test keys de AOSP), Sign.java, Verify.java. JDK 17+.
set -euo pipefail
cd "$(dirname "$0")"
T=tools; B=build; OUT=out
JADX=$T/jadx-1.5.3-all.jar
AJAR=$T/android-27.jar
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dfile.encoding=UTF-8"

rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/dex" "$B/tools" "$OUT"

echo "[1/6] aapt2 compile"
"$T/aapt2" compile --dir res -o "$B/res.zip"

echo "[2/6] aapt2 link"
"$T/aapt2" link -o "$B/base.apk" -I "$AJAR" --manifest AndroidManifest.xml \
  --java "$B/gen" --min-sdk-version 26 --target-sdk-version 27 \
  --no-auto-version --no-version-vectors "$B/res.zip"

echo "[3/6] javac"
find src "$B/gen" -name '*.java' > "$B/sources.txt"
javac --release 8 -encoding UTF-8 -nowarn -Xlint:none -proc:none \
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
javac -proc:none -nowarn -cp "$JADX" -d "$B/tools" "$T/Sign.java" "$T/Verify.java" 2>&1 | grep -v "^Picked up" || true
java -cp "$JADX:$B/tools" Sign "$B/unsigned.apk" "$OUT/launcher-nativo.apk" "$T/platform.pk8" "$T/platform.x509.pem" 2>&1 | grep -v "^Picked up"
java -cp "$JADX:$B/tools" Verify "$OUT/launcher-nativo.apk" 2>&1 | grep -v "^Picked up"
ls -la "$OUT/launcher-nativo.apk"
"$T/aapt2" dump badging "$OUT/launcher-nativo.apk" | grep -E "^package|sdkVersion|targetSdk|launchable|uses-permission: name='android.permission.WRITE_SECURE" || true
echo "OK: $OUT/launcher-nativo.apk"
__EOF__
chmod +x "$D/build.sh"

# ------------------------------------------------------------------ tools/get-tools.sh
cat > "$D/tools/get-tools.sh" <<'__EOF__'
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
__EOF__
chmod +x "$D/tools/get-tools.sh"

# ------------------------------------------------------------------ tools/Sign.java
cat > "$D/tools/Sign.java" <<'__EOF__'
import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

public class Sign {
    public static void main(String[] a) throws Exception {
        File in = new File(a[0]), out = new File(a[1]);
        byte[] pk8 = Files.readAllBytes(Paths.get(a[2]));
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pk8));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(Files.newInputStream(Paths.get(a[3])));
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder("platform", key, List.of(cert)).build();
        new ApkSigner.Builder(List.of(sc)).setInputApk(in).setOutputApk(out)
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(false)
            .setMinSdkVersion(26).build().sign();
        ApkVerifier.Result r = new ApkVerifier.Builder(out).setMinCheckedPlatformVersion(26).build().verify();
        System.out.println("verified=" + r.isVerified() + " v1=" + r.isVerifiedUsingV1Scheme() + " v2=" + r.isVerifiedUsingV2Scheme());
        for (X509Certificate c : r.getSignerCertificates())
            System.out.println("signer sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded())).toUpperCase());
    }
}
__EOF__

# ------------------------------------------------------------------ tools/Verify.java
cat > "$D/tools/Verify.java" <<'__EOF__'
import com.android.apksig.ApkVerifier; import java.io.File; import java.security.*; import java.security.cert.X509Certificate; import java.util.HexFormat;
public class Verify { public static void main(String[] a) throws Exception {
  ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[0])).setMinCheckedPlatformVersion(26).build().verify();
  System.out.println("verified=" + r.isVerified());
  for (X509Certificate c : r.getSignerCertificates()) System.out.println("signer sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(c.getEncoded())).toUpperCase());
}}
__EOF__

# ------------------------------------------------------------------ res/values/strings.xml
cat > "$D/res/values/strings.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Inicio</string>

    <!-- Pantalla principal -->
    <string name="all_apps">Aplicaciones</string>
    <string name="screen_off">Apagar pantalla</string>
    <string name="settings">Ajustes</string>
    <string name="diagnostics">Diagnóstico</string>
    <string name="back">Volver</string>
    <string name="empty_slot">Añadir</string>
    <string name="no_apps">No hay aplicaciones</string>

    <!-- Estado -->
    <string name="bt_connected">Bluetooth conectado</string>
    <string name="bt_on">Bluetooth</string>
    <string name="wifi">Wi-Fi</string>

    <!-- Música -->
    <string name="media_idle">Sin reproducción</string>
    <string name="media_open_hint">Toca para abrir la música</string>
    <string name="media_source_music">Música</string>
    <string name="media_source_radio">Radio</string>
    <string name="media_source_bt">Bluetooth</string>
    <string name="media_source_video">Vídeo</string>
    <string name="media_source_other">Reproductor</string>
    <string name="media_service_offline">Servicio del estéreo no disponible</string>
    <string name="play">Reproducir</string>
    <string name="pause">Pausa</string>
    <string name="next">Siguiente</string>
    <string name="previous">Anterior</string>
    <string name="choose_media_app">App para el widget de música</string>

    <!-- Menú contextual -->
    <string name="add_favorite">Añadir a favoritos</string>
    <string name="remove_favorite">Quitar de favoritos</string>
    <string name="hide_app">Ocultar</string>
    <string name="show_hidden">Mostrar ocultas</string>
    <string name="hide_hidden">Ocultar las ocultas</string>
    <string name="app_info">Información de la app</string>
    <string name="uninstall">Desinstalar</string>
    <string name="favorites_full">Los favoritos están llenos (%1$d). Quita uno primero.</string>

    <!-- Diagnóstico -->
    <string name="diag_title">Diagnóstico del launcher</string>
    <string name="diag_refresh">Actualizar</string>
    <string name="diag_reconnect">Reconectar servicios</string>
    <string name="diag_gc">Forzar GC</string>
    <string name="diag_save">Guardar informe</string>
    <string name="diag_saved">Informe guardado en %1$s</string>
    <string name="diag_save_failed">No se pudo guardar el informe</string>
</resources>
__EOF__

# ------------------------------------------------------------------ res/values-en/strings.xml
cat > "$D/res/values-en/strings.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Home</string>
    <string name="all_apps">Apps</string>
    <string name="screen_off">Screen off</string>
    <string name="settings">Settings</string>
    <string name="diagnostics">Diagnostics</string>
    <string name="back">Back</string>
    <string name="empty_slot">Add</string>
    <string name="no_apps">No apps</string>
    <string name="bt_connected">Bluetooth connected</string>
    <string name="bt_on">Bluetooth</string>
    <string name="wifi">Wi-Fi</string>
    <string name="media_idle">Nothing playing</string>
    <string name="media_open_hint">Tap to open music</string>
    <string name="media_source_music">Music</string>
    <string name="media_source_radio">Radio</string>
    <string name="media_source_bt">Bluetooth</string>
    <string name="media_source_video">Video</string>
    <string name="media_source_other">Player</string>
    <string name="media_service_offline">Head-unit service unavailable</string>
    <string name="play">Play</string>
    <string name="pause">Pause</string>
    <string name="next">Next</string>
    <string name="previous">Previous</string>
    <string name="choose_media_app">App for the music widget</string>
    <string name="add_favorite">Add to favorites</string>
    <string name="remove_favorite">Remove from favorites</string>
    <string name="hide_app">Hide</string>
    <string name="show_hidden">Show hidden</string>
    <string name="hide_hidden">Hide hidden apps</string>
    <string name="app_info">App info</string>
    <string name="uninstall">Uninstall</string>
    <string name="favorites_full">Favorites are full (%1$d). Remove one first.</string>
    <string name="diag_title">Launcher diagnostics</string>
    <string name="diag_refresh">Refresh</string>
    <string name="diag_reconnect">Reconnect services</string>
    <string name="diag_gc">Force GC</string>
    <string name="diag_save">Save report</string>
    <string name="diag_saved">Report saved to %1$s</string>
    <string name="diag_save_failed">Could not save the report</string>
</resources>
__EOF__

# ------------------------------------------------------------------ res/values/colors.xml
cat > "$D/res/values/colors.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Paleta nocturna para cabina: fondo casi negro, superficies grafito, acento ámbar -->
    <color name="bg">#FF0B0D11</color>
    <color name="surface">#FF161A21</color>
    <color name="surface_high">#FF1F242D</color>
    <color name="outline">#FF2A303B</color>
    <color name="text">#FFECEFF4</color>
    <color name="text_dim">#FF8A94A6</color>
    <color name="accent">#FFFFB74D</color>
    <color name="accent_dim">#66FFB74D</color>
    <color name="ok">#FF66BB6A</color>
    <color name="warn">#FFEF5350</color>
    <color name="ripple">#33FFFFFF</color>
    <color name="scrim">#CC0B0D11</color>
</resources>
__EOF__

# ------------------------------------------------------------------ res/values/dimens.xml
cat > "$D/res/values/dimens.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Pantalla de referencia: 1024x600 (se asume mdpi, 1 dp = 1 px) -->
    <dimen name="page_pad">20dp</dimen>
    <dimen name="gap">14dp</dimen>
    <dimen name="card_radius">18dp</dimen>
    <dimen name="tile_radius">16dp</dimen>

    <dimen name="clock_size">104sp</dimen>
    <dimen name="date_size">22sp</dimen>
    <dimen name="status_icon">26dp</dimen>

    <dimen name="cover_size">112dp</dimen>
    <dimen name="media_title">22sp</dimen>
    <dimen name="media_artist">17sp</dimen>
    <dimen name="media_btn">56dp</dimen>
    <dimen name="media_icon">30dp</dimen>

    <dimen name="fav_icon">72dp</dimen>
    <dimen name="fav_label">15sp</dimen>
    <dimen name="app_icon">64dp</dimen>
    <dimen name="app_label">14sp</dimen>

    <dimen name="bar_height">64dp</dimen>
    <dimen name="bar_icon">28dp</dimen>
    <dimen name="bar_text">15sp</dimen>
</resources>
__EOF__

# ------------------------------------------------------------------ res/values/styles.xml
cat > "$D/res/values/styles.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Launcher" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/bg</item>
        <item name="android:colorBackground">@color/bg</item>
        <item name="android:colorPrimary">@color/surface</item>
        <item name="android:colorPrimaryDark">@color/bg</item>
        <item name="android:colorAccent">@color/accent</item>
        <item name="android:textColorPrimary">@color/text</item>
        <item name="android:textColorSecondary">@color/text_dim</item>
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:windowAnimationStyle">@null</item>
        <item name="android:windowDisablePreview">true</item>
        <item name="android:navigationBarColor">@color/bg</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:alertDialogTheme">@style/Theme.Launcher.Dialog</item>
    </style>

    <style name="Theme.Launcher.Dialog" parent="@android:style/Theme.Material.Dialog.Alert">
        <item name="android:colorAccent">@color/accent</item>
        <item name="android:colorBackground">@color/surface_high</item>
        <item name="android:textColorPrimary">@color/text</item>
        <item name="android:textColorSecondary">@color/text_dim</item>
    </style>

    <style name="BarButton">
        <item name="android:layout_width">0dp</item>
        <item name="android:layout_height">match_parent</item>
        <item name="android:layout_weight">1</item>
        <item name="android:gravity">center</item>
        <item name="android:orientation">horizontal</item>
        <item name="android:background">@drawable/bar_item_bg</item>
        <item name="android:clickable">true</item>
        <item name="android:focusable">true</item>
    </style>

    <style name="BarButtonText">
        <item name="android:layout_width">wrap_content</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:layout_marginStart">10dp</item>
        <item name="android:textSize">@dimen/bar_text</item>
        <item name="android:textColor">@color/text</item>
        <item name="android:singleLine">true</item>
    </style>

    <style name="MediaButton">
        <item name="android:layout_width">@dimen/media_btn</item>
        <item name="android:layout_height">@dimen/media_btn</item>
        <item name="android:padding">13dp</item>
        <item name="android:scaleType">fitCenter</item>
        <item name="android:background">@drawable/round_button_bg</item>
        <item name="android:tint">@color/text</item>
    </style>
</resources>
__EOF__

# ------------------------------------------------------------------ res/drawable (vectores)
vec() { # nombre, pathData
cat > "$D/res/drawable/$1.xml" <<__EOF__
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="$2" />
</vector>
__EOF__
}
vec ic_play "M8,5v14l11,-7z"
vec ic_pause "M6,19h4V5H6v14zm8,-14v14h4V5h-4z"
vec ic_skip_next "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z"
vec ic_skip_previous "M6,6h2v12H6zm3.5,6l8.5,6V6z"
vec ic_bluetooth "M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zm1.88,10.46L13,18.17v-3.76l1.88,1.88z"
vec ic_wifi "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zm8,8l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0zm-4,-4l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z"
vec ic_apps "M4,8h4V4H4v4zm6,12h4v-4h-4v4zm-6,0h4v-4H4v4zm0,-6h4v-4H4v4zm6,0h4v-4h-4v4zm6,-10v4h4V4h-4zm-6,4h4V4h-4v4zm6,6h4v-4h-4v4zm0,6h4v-4h-4v4z"
vec ic_power "M13,3h-2v10h2V3zm4.83,2.17l-1.42,1.42C17.99,7.86 19,9.81 19,12c0,3.87 -3.13,7 -7,7s-7,-3.13 -7,-7c0,-2.19 1.01,-4.14 2.58,-5.42L6.17,5.17C4.23,6.82 3,9.26 3,12c0,4.97 4.03,9 9,9s9,-4.03 9,-9c0,-2.74 -1.23,-5.18 -3.17,-6.83z"
vec ic_settings "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"
vec ic_info "M11,17h2v-6h-2v6zm1,-15C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zm0,18c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM11,9h2V7h-2v2z"
vec ic_music_note "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"
vec ic_add "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
vec ic_arrow_back "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"
vec ic_radio "M3.24,6.15C2.51,6.43 2,7.17 2,8v12c0,1.1 0.89,2 2,2h16c1.11,0 2,-0.9 2,-2V8c0,-1.11 -0.89,-2 -2,-2H8.3l8.26,-3.34L15.88,1 3.24,6.15zM7,20c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zm13,-8h-2v-2h-2v2H4V8h16v4z"
vec ic_visibility "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zm0,-8c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"

cat > "$D/res/drawable/ic_launcher.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp"
    android:viewportWidth="48" android:viewportHeight="48">
    <path android:fillColor="#FF161A21" android:pathData="M10,4h28a6,6 0 0 1 6,6v28a6,6 0 0 1 -6,6H10a6,6 0 0 1 -6,-6V10a6,6 0 0 1 6,-6z" />
    <path android:fillColor="#FFFFB74D" android:pathData="M20,38v-10h8v10h8V24h5L24,8 7,24h5v14z" />
</vector>
__EOF__

cat > "$D/res/drawable/card.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/surface" />
    <stroke android:width="1dp" android:color="@color/outline" />
    <corners android:radius="@dimen/card_radius" />
</shape>
__EOF__

cat > "$D/res/drawable/card_clickable.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="@color/ripple">
    <item android:drawable="@drawable/card" />
</ripple>
__EOF__

cat > "$D/res/drawable/tile_bg.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="@color/ripple">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/surface_high" />
            <corners android:radius="@dimen/tile_radius" />
        </shape>
    </item>
</ripple>
__EOF__

cat > "$D/res/drawable/tile_empty_bg.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="@color/ripple">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@android:color/transparent" />
            <stroke android:width="1dp" android:color="@color/outline" android:dashWidth="8dp" android:dashGap="6dp" />
            <corners android:radius="@dimen/tile_radius" />
        </shape>
    </item>
</ripple>
__EOF__

cat > "$D/res/drawable/bar_item_bg.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="@color/ripple">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/surface" />
            <corners android:radius="@dimen/tile_radius" />
        </shape>
    </item>
</ripple>
__EOF__

cat > "$D/res/drawable/round_button_bg.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="@color/ripple">
    <item>
        <shape android:shape="oval">
            <solid android:color="@color/surface_high" />
        </shape>
    </item>
</ripple>
__EOF__

cat > "$D/res/drawable/round_button_accent_bg.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="@color/ripple">
    <item>
        <shape android:shape="oval">
            <solid android:color="@color/accent" />
        </shape>
    </item>
</ripple>
__EOF__

cat > "$D/res/drawable/cover_bg.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/surface_high" />
    <corners android:radius="14dp" />
</shape>
__EOF__

cat > "$D/res/drawable/progress_media.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape android:shape="rectangle">
            <corners android:radius="3dp" />
            <solid android:color="@color/outline" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape android:shape="rectangle">
                <corners android:radius="3dp" />
                <solid android:color="@color/accent" />
            </shape>
        </clip>
    </item>
</layer-list>
__EOF__

# ------------------------------------------------------------------ res/layout/activity_home.xml
cat > "$D/res/layout/activity_home.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg">

    <!-- ===================== Pantalla principal ===================== -->
    <LinearLayout
        android:id="@+id/home_root"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="horizontal"
        android:padding="@dimen/page_pad"
        android:baselineAligned="false">

        <!-- Columna izquierda: reloj + música -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="0.40"
            android:layout_marginEnd="@dimen/gap"
            android:orientation="vertical">

            <!-- Reloj -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="0.46"
                android:background="@drawable/card"
                android:orientation="vertical"
                android:paddingStart="24dp"
                android:paddingEnd="24dp"
                android:paddingTop="12dp"
                android:paddingBottom="14dp">

                <TextView
                    android:id="@+id/clock"
                    android:layout_width="wrap_content"
                    android:layout_height="0dp"
                    android:layout_weight="1"
                    android:gravity="center_vertical"
                    android:fontFamily="sans-serif-thin"
                    android:textColor="@color/text"
                    android:textSize="@dimen/clock_size"
                    android:includeFontPadding="false"
                    android:singleLine="true"
                    android:text="12:00" />

                <TextView
                    android:id="@+id/date"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:textColor="@color/text_dim"
                    android:textSize="@dimen/date_size"
                    android:singleLine="true"
                    android:ellipsize="end"
                    android:text="lunes, 1 de enero" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="10dp"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <ImageView
                        android:id="@+id/status_bt"
                        android:layout_width="@dimen/status_icon"
                        android:layout_height="@dimen/status_icon"
                        android:layout_marginEnd="14dp"
                        android:src="@drawable/ic_bluetooth"
                        android:tint="@color/text_dim"
                        android:contentDescription="@string/bt_on"
                        android:visibility="gone" />

                    <ImageView
                        android:id="@+id/status_wifi"
                        android:layout_width="@dimen/status_icon"
                        android:layout_height="@dimen/status_icon"
                        android:layout_marginEnd="14dp"
                        android:src="@drawable/ic_wifi"
                        android:tint="@color/text_dim"
                        android:contentDescription="@string/wifi"
                        android:visibility="gone" />

                    <TextView
                        android:id="@+id/status_text"
                        android:layout_width="0dp"
                        android:layout_weight="1"
                        android:layout_height="wrap_content"
                        android:textColor="@color/text_dim"
                        android:textSize="14sp"
                        android:singleLine="true"
                        android:ellipsize="end" />
                </LinearLayout>
            </LinearLayout>

            <!-- Música -->
            <LinearLayout
                android:id="@+id/media_card"
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="0.54"
                android:layout_marginTop="@dimen/gap"
                android:background="@drawable/card_clickable"
                android:orientation="vertical"
                android:padding="18dp"
                android:clickable="true"
                android:focusable="true">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="0dp"
                    android:layout_weight="1"
                    android:orientation="horizontal">

                    <ImageView
                        android:id="@+id/media_cover"
                        android:layout_width="@dimen/cover_size"
                        android:layout_height="@dimen/cover_size"
                        android:background="@drawable/cover_bg"
                        android:scaleType="centerCrop"
                        android:padding="0dp"
                        android:src="@drawable/ic_music_note"
                        android:tint="@color/text_dim" />

                    <LinearLayout
                        android:layout_width="0dp"
                        android:layout_height="match_parent"
                        android:layout_weight="1"
                        android:layout_marginStart="18dp"
                        android:orientation="vertical"
                        android:gravity="center_vertical">

                        <TextView
                            android:id="@+id/media_source"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:textColor="@color/accent"
                            android:textSize="13sp"
                            android:textAllCaps="true"
                            android:letterSpacing="0.08"
                            android:singleLine="true"
                            android:text="@string/media_source_music" />

                        <TextView
                            android:id="@+id/media_title"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="4dp"
                            android:textColor="@color/text"
                            android:textSize="@dimen/media_title"
                            android:fontFamily="sans-serif-medium"
                            android:singleLine="true"
                            android:ellipsize="marquee"
                            android:marqueeRepeatLimit="marquee_forever"
                            android:text="@string/media_idle" />

                        <TextView
                            android:id="@+id/media_artist"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="2dp"
                            android:textColor="@color/text_dim"
                            android:textSize="@dimen/media_artist"
                            android:singleLine="true"
                            android:ellipsize="end"
                            android:text="@string/media_open_hint" />

                        <ProgressBar
                            android:id="@+id/media_progress"
                            style="?android:attr/progressBarStyleHorizontal"
                            android:layout_width="match_parent"
                            android:layout_height="6dp"
                            android:layout_marginTop="12dp"
                            android:progressDrawable="@drawable/progress_media"
                            android:max="1000"
                            android:progress="0" />

                        <TextView
                            android:id="@+id/media_time"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="4dp"
                            android:textColor="@color/text_dim"
                            android:textSize="12sp"
                            android:fontFamily="sans-serif"
                            android:singleLine="true"
                            android:text="" />
                    </LinearLayout>
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="14dp"
                    android:orientation="horizontal"
                    android:gravity="center">

                    <ImageButton
                        android:id="@+id/media_prev"
                        style="@style/MediaButton"
                        android:layout_marginEnd="22dp"
                        android:src="@drawable/ic_skip_previous"
                        android:contentDescription="@string/previous" />

                    <ImageButton
                        android:id="@+id/media_play"
                        style="@style/MediaButton"
                        android:layout_width="68dp"
                        android:layout_height="68dp"
                        android:background="@drawable/round_button_accent_bg"
                        android:tint="@color/bg"
                        android:src="@drawable/ic_play"
                        android:contentDescription="@string/play" />

                    <ImageButton
                        android:id="@+id/media_next"
                        style="@style/MediaButton"
                        android:layout_marginStart="22dp"
                        android:src="@drawable/ic_skip_next"
                        android:contentDescription="@string/next" />
                </LinearLayout>
            </LinearLayout>
        </LinearLayout>

        <!-- Columna derecha: favoritos + barra -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="0.60"
            android:orientation="vertical">

            <GridView
                android:id="@+id/fav_grid"
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="1"
                android:numColumns="4"
                android:stretchMode="columnWidth"
                android:horizontalSpacing="@dimen/gap"
                android:verticalSpacing="@dimen/gap"
                android:listSelector="@android:color/transparent"
                android:scrollbars="none"
                android:overScrollMode="never"
                android:cacheColorHint="@android:color/transparent" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="@dimen/bar_height"
                android:layout_marginTop="@dimen/gap"
                android:orientation="horizontal"
                android:baselineAligned="false">

                <LinearLayout android:id="@+id/btn_all_apps" style="@style/BarButton" android:layout_marginEnd="@dimen/gap">
                    <ImageView android:layout_width="@dimen/bar_icon" android:layout_height="@dimen/bar_icon" android:src="@drawable/ic_apps" android:tint="@color/accent" />
                    <TextView style="@style/BarButtonText" android:text="@string/all_apps" />
                </LinearLayout>

                <LinearLayout android:id="@+id/btn_screen_off" style="@style/BarButton" android:layout_marginEnd="@dimen/gap">
                    <ImageView android:layout_width="@dimen/bar_icon" android:layout_height="@dimen/bar_icon" android:src="@drawable/ic_power" android:tint="@color/text" />
                    <TextView style="@style/BarButtonText" android:text="@string/screen_off" />
                </LinearLayout>

                <LinearLayout android:id="@+id/btn_settings" style="@style/BarButton" android:layout_marginEnd="@dimen/gap">
                    <ImageView android:layout_width="@dimen/bar_icon" android:layout_height="@dimen/bar_icon" android:src="@drawable/ic_settings" android:tint="@color/text" />
                    <TextView style="@style/BarButtonText" android:text="@string/settings" />
                </LinearLayout>

                <LinearLayout android:id="@+id/btn_diag" style="@style/BarButton">
                    <ImageView android:layout_width="@dimen/bar_icon" android:layout_height="@dimen/bar_icon" android:src="@drawable/ic_info" android:tint="@color/text" />
                    <TextView style="@style/BarButtonText" android:text="@string/diagnostics" />
                </LinearLayout>
            </LinearLayout>
        </LinearLayout>
    </LinearLayout>

    <!-- ===================== Panel "todas las apps" ===================== -->
    <LinearLayout
        android:id="@+id/apps_panel"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:background="@color/bg"
        android:padding="@dimen/page_pad"
        android:visibility="gone"
        android:clickable="true"
        android:focusable="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="56dp"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <ImageButton
                android:id="@+id/btn_apps_back"
                android:layout_width="56dp"
                android:layout_height="56dp"
                android:padding="14dp"
                android:scaleType="fitCenter"
                android:background="@drawable/round_button_bg"
                android:src="@drawable/ic_arrow_back"
                android:tint="@color/text"
                android:contentDescription="@string/back" />

            <TextView
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="wrap_content"
                android:layout_marginStart="18dp"
                android:text="@string/all_apps"
                android:textColor="@color/text"
                android:textSize="24sp"
                android:fontFamily="sans-serif-medium" />

            <TextView
                android:id="@+id/apps_count"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="18dp"
                android:textColor="@color/text_dim"
                android:textSize="15sp" />

            <ImageButton
                android:id="@+id/btn_toggle_hidden"
                android:layout_width="56dp"
                android:layout_height="56dp"
                android:padding="14dp"
                android:scaleType="fitCenter"
                android:background="@drawable/round_button_bg"
                android:src="@drawable/ic_visibility"
                android:tint="@color/text_dim"
                android:contentDescription="@string/show_hidden" />
        </LinearLayout>

        <GridView
            android:id="@+id/apps_grid"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:layout_marginTop="@dimen/gap"
            android:numColumns="6"
            android:stretchMode="columnWidth"
            android:horizontalSpacing="@dimen/gap"
            android:verticalSpacing="@dimen/gap"
            android:listSelector="@android:color/transparent"
            android:fastScrollEnabled="false"
            android:scrollbarStyle="outsideOverlay"
            android:cacheColorHint="@android:color/transparent" />
    </LinearLayout>
</FrameLayout>
__EOF__

# ------------------------------------------------------------------ res/layout/item_fav.xml
cat > "$D/res/layout/item_fav.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/tile"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="center"
    android:background="@drawable/tile_bg"
    android:paddingTop="16dp"
    android:paddingBottom="14dp"
    android:paddingStart="8dp"
    android:paddingEnd="8dp">

    <ImageView
        android:id="@+id/icon"
        android:layout_width="@dimen/fav_icon"
        android:layout_height="@dimen/fav_icon"
        android:scaleType="fitCenter" />

    <TextView
        android:id="@+id/label"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="10dp"
        android:gravity="center"
        android:textColor="@color/text"
        android:textSize="@dimen/fav_label"
        android:singleLine="true"
        android:ellipsize="end" />
</LinearLayout>
__EOF__

# ------------------------------------------------------------------ res/layout/item_app.xml
cat > "$D/res/layout/item_app.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/tile"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="center"
    android:background="@drawable/tile_bg"
    android:paddingTop="14dp"
    android:paddingBottom="12dp"
    android:paddingStart="6dp"
    android:paddingEnd="6dp">

    <ImageView
        android:id="@+id/icon"
        android:layout_width="@dimen/app_icon"
        android:layout_height="@dimen/app_icon"
        android:scaleType="fitCenter" />

    <TextView
        android:id="@+id/label"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:gravity="center"
        android:textColor="@color/text"
        android:textSize="@dimen/app_label"
        android:singleLine="true"
        android:ellipsize="end" />
</LinearLayout>
__EOF__

# ------------------------------------------------------------------ res/layout/activity_diag.xml
cat > "$D/res/layout/activity_diag.xml" <<'__EOF__'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/bg"
    android:padding="@dimen/page_pad">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <ImageButton
            android:id="@+id/btn_back"
            android:layout_width="56dp"
            android:layout_height="56dp"
            android:padding="14dp"
            android:scaleType="fitCenter"
            android:background="@drawable/round_button_bg"
            android:src="@drawable/ic_arrow_back"
            android:tint="@color/text"
            android:contentDescription="@string/back" />

        <TextView
            android:layout_width="0dp"
            android:layout_weight="1"
            android:layout_height="wrap_content"
            android:layout_marginStart="18dp"
            android:text="@string/diag_title"
            android:textColor="@color/text"
            android:textSize="24sp"
            android:fontFamily="sans-serif-medium" />

        <Button android:id="@+id/btn_refresh" android:layout_width="wrap_content" android:layout_height="44dp" android:text="@string/diag_refresh" android:textColor="@color/text" android:background="@drawable/bar_item_bg" android:paddingStart="18dp" android:paddingEnd="18dp" android:layout_marginEnd="10dp" />
        <Button android:id="@+id/btn_reconnect" android:layout_width="wrap_content" android:layout_height="44dp" android:text="@string/diag_reconnect" android:textColor="@color/text" android:background="@drawable/bar_item_bg" android:paddingStart="18dp" android:paddingEnd="18dp" android:layout_marginEnd="10dp" />
        <Button android:id="@+id/btn_gc" android:layout_width="wrap_content" android:layout_height="44dp" android:text="@string/diag_gc" android:textColor="@color/text" android:background="@drawable/bar_item_bg" android:paddingStart="18dp" android:paddingEnd="18dp" android:layout_marginEnd="10dp" />
        <Button android:id="@+id/btn_save" android:layout_width="wrap_content" android:layout_height="44dp" android:text="@string/diag_save" android:textColor="@color/bg" android:background="@drawable/round_button_accent_bg" android:paddingStart="18dp" android:paddingEnd="18dp" />
    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginTop="@dimen/gap"
        android:background="@drawable/card"
        android:padding="16dp">

        <TextView
            android:id="@+id/report"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:fontFamily="monospace"
            android:textColor="@color/text"
            android:textSize="13sp"
            android:textIsSelectable="true" />
    </ScrollView>
</LinearLayout>
__EOF__

# ------------------------------------------------------------------ src: Diag.java
cat > "$D/src/com/android/launcher3/Diag.java" <<'__EOF__'
package com.android.launcher3;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Registro circular en memoria (últimas líneas) para la pantalla de diagnóstico.
 * Barato: una línea por evento relevante, nunca en bucles de dibujo.
 */
public final class Diag {
    private static final String TAG = "LauncherNativo";
    private static final int MAX_LINES = 250;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>(MAX_LINES);
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static final Date SCRATCH = new Date();

    private Diag() {}

    public static void log(String where, String msg) {
        Log.d(TAG, where + ": " + msg);
        synchronized (LINES) {
            if (LINES.size() >= MAX_LINES) LINES.removeFirst();
            SCRATCH.setTime(System.currentTimeMillis());
            LINES.addLast(TIME.format(SCRATCH) + ' ' + where + ": " + msg);
        }
    }

    public static void warn(String where, String msg, Throwable t) {
        Log.w(TAG, where + ": " + msg, t);
        log(where, msg + (t != null ? " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")" : ""));
    }

    public static String dump() {
        StringBuilder sb = new StringBuilder(8192);
        synchronized (LINES) {
            for (String l : LINES) sb.append(l).append('\n');
        }
        return sb.toString();
    }
}
__EOF__

# ------------------------------------------------------------------ src: Prefs.java
cat > "$D/src/com/android/launcher3/Prefs.java" <<'__EOF__'
package com.android.launcher3;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Preferencias del launcher. Escrituras con apply() (asíncronas), nunca fsync en el hilo principal. */
public final class Prefs {
    private static final String FILE = "launcher";
    private static final String KEY_FAVORITES = "favorites";   // componentes separados por '\n'
    private static final String KEY_HIDDEN = "hidden";         // StringSet de componentes
    private static final String KEY_MEDIA_APP = "media_app";   // paquete que abre el widget de música
    private static final String KEY_SHOW_HIDDEN = "show_hidden";

    /** Paquete de la app de música de fábrica (IVISystem.PACKAGE_MUSIC en el SDK Jancar). */
    public static final String DEFAULT_MEDIA_APP = "com.jancar.music";
    public static final int MAX_FAVORITES = 8;

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public List<String> getFavorites() {
        String raw = sp.getString(KEY_FAVORITES, null);
        if (raw == null) return defaultFavorites();
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\n")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    public void setFavorites(List<String> favs) {
        StringBuilder sb = new StringBuilder();
        for (String f : favs) { if (sb.length() > 0) sb.append('\n'); sb.append(f); }
        sp.edit().putString(KEY_FAVORITES, sb.toString()).apply();
    }

    /** Favoritos iniciales: apps de fábrica habituales; las que no existan se ignoran al resolver. */
    private static List<String> defaultFavorites() {
        List<String> l = new ArrayList<>();
        l.add("pkg:com.jancar.music");
        l.add("pkg:com.jancar.radio");
        l.add("pkg:com.jancar.bluetooth");
        l.add("pkg:com.jancar.video");
        l.add("pkg:com.jancar.settings");
        return Collections.unmodifiableList(l);
    }

    public Set<String> getHidden() {
        return new HashSet<>(sp.getStringSet(KEY_HIDDEN, Collections.<String>emptySet()));
    }

    public void setHidden(Set<String> hidden) {
        sp.edit().putStringSet(KEY_HIDDEN, new HashSet<>(hidden)).apply();
    }

    public boolean getShowHidden() { return sp.getBoolean(KEY_SHOW_HIDDEN, false); }

    public void setShowHidden(boolean v) { sp.edit().putBoolean(KEY_SHOW_HIDDEN, v).apply(); }

    public String getMediaApp() { return sp.getString(KEY_MEDIA_APP, DEFAULT_MEDIA_APP); }

    public void setMediaApp(String pkg) { sp.edit().putString(KEY_MEDIA_APP, pkg).apply(); }
}
__EOF__

# ------------------------------------------------------------------ src: AppEntry.java
cat > "$D/src/com/android/launcher3/AppEntry.java" <<'__EOF__'
package com.android.launcher3;

import android.content.ComponentName;

/** Una actividad lanzable. Inmutable; el icono vive aparte, en la caché. */
public final class AppEntry {
    public final ComponentName component;
    public final String packageName;
    public final String label;
    /** Clave estable: ComponentName.flattenToString(). */
    public final String key;

    public AppEntry(ComponentName component, String label) {
        this.component = component;
        this.packageName = component.getPackageName();
        this.label = label;
        this.key = component.flattenToString();
    }

    @Override public boolean equals(Object o) {
        return o instanceof AppEntry && ((AppEntry) o).key.equals(key);
    }

    @Override public int hashCode() { return key.hashCode(); }

    @Override public String toString() { return label + " [" + key + "]"; }
}
__EOF__

# ------------------------------------------------------------------ src: BuildInfo.java
cat > "$D/src/com/android/launcher3/BuildInfo.java" <<'__EOF__'
package com.android.launcher3;

/** Versión legible; el build.sh la mantiene alineada con el manifiesto. */
public final class BuildInfo {
    public static final String VERSION = "3.0.0-nativo (2021)";
    private BuildInfo() {}
}
__EOF__

# ------------------------------------------------------------------ src: AppsRepository.java
cat > "$D/src/com/android/launcher3/AppsRepository.java" <<'__EOF__'
package com.android.launcher3;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lista de aplicaciones lanzables + caché de iconos reducidos.
 *
 * Reglas:
 *  - PackageManager y decodificación de iconos SIEMPRE en el hilo de fondo único "launcher-bg".
 *  - Los iconos se rasterizan al tamaño de celda (p. ej. 72 px) y viven en una LruCache acotada;
 *    nunca se guarda el Drawable original de otra app.
 *  - Un cambio de paquete provoca un refresco de la lista (barato) y expulsa solo los iconos de ese paquete.
 */
public final class AppsRepository {

    public interface Listener { void onAppsChanged(List<AppEntry> apps); }

    private static final int ICON_CACHE_BYTES = 3 * 1024 * 1024; // ~3 MB: 60 iconos de 72 px no llegan a 1,3 MB

    private final Context app;
    private final PackageManager pm;
    private final Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final LruCache<String, Bitmap> icons = new LruCache<String, Bitmap>(ICON_CACHE_BYTES) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };

    private volatile List<AppEntry> apps = Collections.emptyList();
    private boolean refreshPending;
    private int refreshCount;

    public AppsRepository(Context context) {
        app = context.getApplicationContext();
        pm = app.getPackageManager();
        HandlerThread t = new HandlerThread("launcher-bg", Process.THREAD_PRIORITY_BACKGROUND);
        t.start();
        bg = new Handler(t.getLooper());

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        app.registerReceiver(packageReceiver, f);
    }

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String pkg = i.getData() != null ? i.getData().getSchemeSpecificPart() : null;
            Diag.log("apps", i.getAction() + " " + pkg);
            if (pkg != null) evictPackage(pkg);
            // ADDED+REPLACED llegan seguidos: un solo refresco diferido
            refresh();
        }
    };

    // ------------------------------------------------------------------ lista

    public List<AppEntry> getApps() { return apps; }

    public int getRefreshCount() { return refreshCount; }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    /** Coalesce: varias peticiones seguidas producen un único escaneo. */
    public void refresh() {
        if (refreshPending) return;
        refreshPending = true;
        main.postDelayed(new Runnable() {
            @Override public void run() {
                refreshPending = false;
                bg.post(scanRunnable);
            }
        }, 300);
    }

    private final Runnable scanRunnable = new Runnable() {
        @Override public void run() {
            long t0 = System.nanoTime();
            final List<AppEntry> list = scan();
            refreshCount++;
            Diag.log("apps", list.size() + " apps en " + (System.nanoTime() - t0) / 1000000 + " ms");
            main.post(new Runnable() {
                @Override public void run() {
                    apps = list;
                    for (Listener l : new ArrayList<>(listeners)) l.onAppsChanged(list);
                }
            });
        }
    };

    private List<AppEntry> scan() {
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris;
        try {
            ris = pm.queryIntentActivities(probe, 0);
        } catch (RuntimeException e) {
            Diag.warn("apps", "queryIntentActivities", e);
            return apps; // conserva la lista anterior
        }
        String self = app.getPackageName();
        List<AppEntry> out = new ArrayList<>(ris.size());
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo == null || self.equals(ri.activityInfo.packageName)) continue;
            CharSequence label = null;
            try { label = ri.loadLabel(pm); } catch (RuntimeException ignored) {}
            String text = label != null ? label.toString().trim() : ri.activityInfo.packageName;
            out.add(new AppEntry(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), text));
        }
        final Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) { return collator.compare(a.label, b.label); }
        });
        return out;
    }

    /** Resuelve una clave de favorito: "pkg:<paquete>" (primera actividad del paquete) o ComponentName aplanado. */
    public AppEntry resolve(String key) {
        List<AppEntry> cur = apps;
        if (key.startsWith("pkg:")) {
            String pkg = key.substring(4);
            for (AppEntry e : cur) if (e.packageName.equals(pkg)) return e;
            return null;
        }
        for (AppEntry e : cur) if (e.key.equals(key)) return e;
        return null;
    }

    public AppEntry findByPackage(String pkg) {
        for (AppEntry e : apps) if (e.packageName.equals(pkg)) return e;
        return null;
    }

    // ------------------------------------------------------------------ iconos

    public int getIconCacheBytes() { return icons.size(); }

    public int getIconCacheCount() { return icons.snapshot().size(); }

    public void trimIcons(boolean all) {
        if (all) icons.evictAll(); else icons.trimToSize(icons.size() / 2);
    }

    private void evictPackage(String pkg) {
        for (String k : icons.snapshot().keySet()) {
            if (k.startsWith(pkg + "/")) icons.remove(k);
        }
    }

    /**
     * Pone el icono en el ImageView: inmediato si está en caché; si no, lo rasteriza en segundo plano
     * y lo aplica solo si la celda sigue mostrando la misma app (tag).
     */
    public void bindIcon(final AppEntry entry, final ImageView target, final int sizePx) {
        final String key = entry.key + "@" + sizePx;
        target.setTag(key);
        Bitmap cached = icons.get(key);
        if (cached != null) { target.setImageBitmap(cached); return; }
        target.setImageDrawable(null);
        bg.post(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = renderIcon(entry, sizePx);
                if (bmp == null) return;
                icons.put(key, bmp);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (key.equals(target.getTag())) target.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    private Bitmap renderIcon(AppEntry entry, int sizePx) {
        Drawable d;
        try {
            d = pm.getActivityIcon(entry.component);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            try { d = pm.getDefaultActivityIcon(); } catch (RuntimeException e2) { return null; }
        }
        if (d == null) return null;
        try {
            if (d instanceof BitmapDrawable) {
                Bitmap src = ((BitmapDrawable) d).getBitmap();
                if (src != null && src.getWidth() == sizePx && src.getHeight() == sizePx) return src;
            }
            Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            d.setBounds(0, 0, sizePx, sizePx);
            d.draw(c);
            return out;
        } catch (RuntimeException | OutOfMemoryError e) {
            Diag.warn("apps", "icono " + entry.packageName, e);
            return null;
        }
    }

    // ------------------------------------------------------------------ lanzar

    public void launch(Context ctx, AppEntry entry) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(entry.component)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
            Diag.log("apps", "abrir " + entry.key);
        } catch (ActivityNotFoundException | SecurityException e) {
            Diag.warn("apps", "no se pudo abrir " + entry.key, e);
            Toast.makeText(ctx, entry.label, Toast.LENGTH_SHORT).show();
            refresh();
        }
    }

    public boolean launchPackage(Context ctx, String pkg) {
        Intent i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { ctx.startActivity(i); return true; }
        catch (ActivityNotFoundException | SecurityException e) { Diag.warn("apps", "no se pudo abrir " + pkg, e); return false; }
    }
}
__EOF__

# ------------------------------------------------------------------ src: LauncherApp.java
cat > "$D/src/com/android/launcher3/LauncherApp.java" <<'__EOF__'
package com.android.launcher3;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.android.launcher3.jancar.JancarBridge;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Proceso del launcher (persistente, uid system). Mantiene los tres objetos de larga vida:
 * preferencias, repositorio de apps (con su hilo de fondo) y el puente a com.jancar.services.
 */
public final class LauncherApp extends Application {
    /** Igual que el launcher de fábrica: 6 s tras arrancar pide al servicio reabrir la última app multimedia. */
    private static final long MEMORY_APP_DELAY_MS = 6000;

    private static LauncherApp instance;
    private Prefs prefs;
    private AppsRepository apps;
    private JancarBridge bridge;
    private final long startedAt = SystemClock.elapsedRealtime();
    private boolean memoryAppRequested;

    public static LauncherApp get() { return instance; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        installCrashLogger();
        Diag.log("app", "inicio " + BuildInfo.VERSION);
        prefs = new Prefs(this);
        apps = new AppsRepository(this);
        bridge = new JancarBridge(this);
        bridge.start();
        apps.refresh();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { maybeOpenMemoryApp(); }
        }, MEMORY_APP_DELAY_MS);
    }

    private void maybeOpenMemoryApp() {
        if (memoryAppRequested) return;
        memoryAppRequested = true;
        // El launcher de fábrica solo lo pide si él mismo está en primer plano (es decir, arranque normal).
        if (bridge.isHomeVisible()) bridge.openMemoryApp();
        else Diag.log("system", "openMemoryApp omitido: launcher no visible");
    }

    public Prefs prefs() { return prefs; }

    public AppsRepository apps() { return apps; }

    public JancarBridge bridge() { return bridge; }

    public long uptimeMs() { return SystemClock.elapsedRealtime() - startedAt; }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            apps.trimIcons(true);
            Diag.log("app", "trim " + level + ": caché de iconos vaciada");
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            apps.trimIcons(false);
        }
    }

    /**
     * El de fábrica (CrashLog) mataba el proceso ante cualquier excepción; aquí solo dejamos
     * constancia en un archivo propio y dejamos actuar al manejador del sistema.
     */
    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    File f = new File(getFilesDir(), "crash.txt");
                    PrintWriter pw = new PrintWriter(new FileWriter(f, false));
                    pw.println("version " + BuildInfo.VERSION + " uptime " + uptimeMs() + " ms, hilo " + t.getName());
                    e.printStackTrace(pw);
                    pw.println();
                    pw.print(Diag.dump());
                    pw.close();
                } catch (Throwable ignored) {}
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }
}
__EOF__

# ------------------------------------------------------------------ src: HomeActivity.java
cat > "$D/src/com/android/launcher3/HomeActivity.java" <<'__EOF__'
package com.android.launcher3;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.jancar.JancarBridge;
import com.android.launcher3.jancar.MediaState;
import com.android.launcher3.ui.ClockController;
import com.android.launcher3.ui.MediaWidget;
import com.android.launcher3.ui.StatusController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Única pantalla: inicio (reloj, música, favoritos, barra) y el panel de todas las apps,
 * que es una vista superpuesta dentro de la misma Activity (sin segunda Activity ni animaciones).
 */
public final class HomeActivity extends Activity implements AppsRepository.Listener, JancarBridge.Listener {

    private Prefs prefs;
    private AppsRepository apps;
    private JancarBridge bridge;

    private View homeRoot;
    private View appsPanel;
    private GridView favGrid;
    private GridView appsGrid;
    private TextView appsCount;
    private ImageView btnToggleHidden;
    private ClockController clock;
    private StatusController status;
    private MediaWidget mediaWidget;
    private FavAdapter favAdapter;
    private AppsAdapter appsAdapter;

    private List<String> favKeys;
    private Set<String> hidden;
    private boolean showHidden;
    private int favIconPx;
    private int appIconPx;
    private boolean globalsWritten;

    // ------------------------------------------------------------------ ciclo de vida

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LauncherApp app = LauncherApp.get();
        prefs = app.prefs();
        apps = app.apps();
        bridge = app.bridge();
        favKeys = new ArrayList<>(prefs.getFavorites());
        hidden = prefs.getHidden();
        showHidden = prefs.getShowHidden();
        favIconPx = getResources().getDimensionPixelSize(R.dimen.fav_icon);
        appIconPx = getResources().getDimensionPixelSize(R.dimen.app_icon);

        setContentView(R.layout.activity_home);
        homeRoot = findViewById(R.id.home_root);
        appsPanel = findViewById(R.id.apps_panel);
        favGrid = findViewById(R.id.fav_grid);
        appsGrid = findViewById(R.id.apps_grid);
        appsCount = findViewById(R.id.apps_count);
        btnToggleHidden = findViewById(R.id.btn_toggle_hidden);

        clock = new ClockController(this, (TextView) findViewById(R.id.clock), (TextView) findViewById(R.id.date));
        status = new StatusController(this, (ImageView) findViewById(R.id.status_bt), (ImageView) findViewById(R.id.status_wifi),
                (TextView) findViewById(R.id.status_text));
        View mediaCard = findViewById(R.id.media_card);
        mediaWidget = new MediaWidget(mediaCard);

        favAdapter = new FavAdapter();
        favGrid.setAdapter(favAdapter);
        favGrid.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int h = b - t;
                if (h > 0 && h != (ob - ot)) favAdapter.setRowHeight((h - favGrid.getVerticalSpacing()) / 2);
            }
        });
        appsAdapter = new AppsAdapter();
        appsGrid.setAdapter(appsAdapter);
        appsGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                AppEntry e = appsAdapter.getItem(pos);
                if (e != null) { apps.launch(HomeActivity.this, e); }
            }
        });
        appsGrid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                AppEntry e = appsAdapter.getItem(pos);
                if (e != null) showAppMenu(e, false);
                return true;
            }
        });

        mediaCard.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openMediaApp(); } });
        mediaCard.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { chooseMediaApp(); return true; } });
        findViewById(R.id.media_play).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { bridge.mediaPlayPause(); } });
        findViewById(R.id.media_next).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { bridge.mediaNext(); } });
        findViewById(R.id.media_prev).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { bridge.mediaPrev(); } });

        findViewById(R.id.btn_all_apps).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showApps(); } });
        findViewById(R.id.btn_screen_off).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!bridge.screenOff()) Toast.makeText(HomeActivity.this, R.string.media_service_offline, Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openSettings(); } });
        findViewById(R.id.btn_diag).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(HomeActivity.this, DiagnosticsActivity.class)); }
        });
        findViewById(R.id.btn_apps_back).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showHome(); } });
        btnToggleHidden.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showHidden = !showHidden;
                prefs.setShowHidden(showHidden);
                refreshApps();
            }
        });
        Diag.log("home", "onCreate");
    }

    @Override protected void onStart() {
        super.onStart();
        apps.addListener(this);
        bridge.addListener(this);
        onAppsChanged(apps.getApps());
        onMediaChanged(bridge.getMedia());
    }

    @Override protected void onResume() {
        super.onResume();
        bridge.setHomeVisible(true);
        clock.start();
        status.start();
        bridge.requestMediaState();
        writeSystemUiGlobals();
    }

    @Override protected void onPause() {
        bridge.setHomeVisible(false);
        clock.stop();
        status.stop();
        super.onPause();
    }

    @Override protected void onStop() {
        apps.removeListener(this);
        bridge.removeListener(this);
        super.onStop();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showHome(); // tecla HOME estando en "todas las apps"
    }

    @Override public void onBackPressed() {
        if (appsPanel.getVisibility() == View.VISIBLE) showHome();
        // En la pantalla de inicio, "atrás" no hace nada (es la HOME).
    }

    /** Valores que SystemUI de Jancar consulta; el launcher de fábrica escribe 0 en ambos para los temas normales. */
    private void writeSystemUiGlobals() {
        if (globalsWritten) return;
        globalsWritten = true;
        try {
            Settings.Global.putInt(getContentResolver(), "jancar_sysui_home_enter", 0);
            Settings.Global.putInt(getContentResolver(), "LauncherActivityView", 0);
        } catch (SecurityException e) {
            Diag.warn("home", "Settings.Global (¿sin uid system?)", e);
        }
    }

    // ------------------------------------------------------------------ navegación interna

    private void showApps() {
        refreshApps();
        appsPanel.setVisibility(View.VISIBLE);
        homeRoot.setVisibility(View.INVISIBLE);
    }

    private void showHome() {
        if (appsPanel.getVisibility() != View.VISIBLE) return;
        appsPanel.setVisibility(View.GONE);
        homeRoot.setVisibility(View.VISIBLE);
    }

    private void refreshApps() {
        List<AppEntry> all = apps.getApps();
        List<AppEntry> shown = new ArrayList<>(all.size());
        for (AppEntry e : all) if (showHidden || !hidden.contains(e.key)) shown.add(e);
        appsAdapter.setItems(shown);
        appsCount.setText(shown.size() + (hidden.isEmpty() ? "" : " · " + hidden.size() + " ocultas"));
        btnToggleHidden.setImageTintList(ColorStateList.valueOf(getColor(showHidden ? R.color.accent : R.color.text_dim)));
        btnToggleHidden.setVisibility(hidden.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ AppsRepository.Listener

    @Override public void onAppsChanged(List<AppEntry> list) {
        favAdapter.rebuild();
        if (appsPanel.getVisibility() == View.VISIBLE) refreshApps();
    }

    // ------------------------------------------------------------------ JancarBridge.Listener

    @Override public void onMediaChanged(MediaState state) { mediaWidget.render(state, bridge.isMediaOnline()); }

    @Override public void onServicesChanged() { mediaWidget.render(bridge.getMedia(), bridge.isMediaOnline()); }

    @Override public void onAccChanged(boolean on) { /* el servicio apaga la pantalla; nada que hacer aquí */ }

    @Override public void onCcdChanged(int status) { /* la cámara la dibuja el sistema */ }

    // ------------------------------------------------------------------ acciones

    private void openMediaApp() {
        String pkg;
        switch (bridge.getMedia().type) {
            case MediaState.TYPE_A2DP: pkg = "com.jancar.bluetooth"; break;
            case MediaState.TYPE_RADIO: pkg = "com.jancar.radio"; break;
            case MediaState.TYPE_VIDEO: pkg = "com.jancar.video"; break;
            default: pkg = prefs.getMediaApp();
        }
        if (!apps.launchPackage(this, pkg) && !apps.launchPackage(this, prefs.getMediaApp())) {
            chooseMediaApp();
        }
    }

    private void chooseMediaApp() {
        final List<AppEntry> list = apps.getApps();
        if (list.isEmpty()) return;
        String[] labels = new String[list.size()];
        int checked = -1;
        String cur = prefs.getMediaApp();
        for (int i = 0; i < list.size(); i++) {
            labels[i] = list.get(i).label;
            if (checked < 0 && list.get(i).packageName.equals(cur)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_media_app)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        prefs.setMediaApp(list.get(which).packageName);
                        Diag.log("home", "app de música: " + list.get(which).packageName);
                        d.dismiss();
                    }
                })
                .show();
    }

    private void openSettings() {
        if (apps.launchPackage(this, "com.jancar.settings")) return;
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.settings, Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppMenu(final AppEntry e, final boolean fromFavorites) {
        final boolean isFav = favKeys.contains(e.key) || favKeys.contains("pkg:" + e.packageName);
        final boolean isHidden = hidden.contains(e.key);
        final List<String> items = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        items.add(getString(isFav ? R.string.remove_favorite : R.string.add_favorite));
        actions.add(new Runnable() { @Override public void run() { if (isFav) removeFavorite(e); else addFavorite(e); } });
        if (!fromFavorites) {
            items.add(getString(isHidden ? R.string.show_hidden : R.string.hide_app));
            actions.add(new Runnable() {
                @Override public void run() {
                    if (isHidden) hidden.remove(e.key); else hidden.add(e.key);
                    prefs.setHidden(hidden);
                    refreshApps();
                }
            });
        }
        items.add(getString(R.string.app_info));
        actions.add(new Runnable() {
            @Override public void run() {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ex) { Toast.makeText(HomeActivity.this, e.packageName, Toast.LENGTH_SHORT).show(); }
            }
        });
        items.add(getString(R.string.uninstall));
        actions.add(new Runnable() {
            @Override public void run() {
                try {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + e.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ex) { Toast.makeText(HomeActivity.this, e.packageName, Toast.LENGTH_SHORT).show(); }
            }
        });
        new AlertDialog.Builder(this)
                .setTitle(e.label)
                .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) { actions.get(which).run(); }
                })
                .show();
    }

    private void addFavorite(AppEntry e) {
        // Normaliza las claves "pkg:" heredadas de los valores por defecto
        List<String> resolved = new ArrayList<>();
        for (String k : favKeys) { AppEntry r = apps.resolve(k); if (r != null) resolved.add(r.key); }
        if (resolved.contains(e.key)) return;
        if (resolved.size() >= Prefs.MAX_FAVORITES) {
            Toast.makeText(this, getString(R.string.favorites_full, Prefs.MAX_FAVORITES), Toast.LENGTH_SHORT).show();
            return;
        }
        resolved.add(e.key);
        favKeys = resolved;
        prefs.setFavorites(favKeys);
        favAdapter.rebuild();
        Toast.makeText(this, e.label, Toast.LENGTH_SHORT).show();
    }

    private void removeFavorite(AppEntry e) {
        List<String> resolved = new ArrayList<>();
        for (String k : favKeys) { AppEntry r = apps.resolve(k); if (r != null && !r.key.equals(e.key)) resolved.add(r.key); }
        favKeys = resolved;
        prefs.setFavorites(favKeys);
        favAdapter.rebuild();
    }

    // ------------------------------------------------------------------ adaptadores

    private static final class Holder {
        ImageView icon; TextView label; AppEntry entry;
    }

    /** 8 huecos fijos (4×2). Un hueco vacío abre "todas las apps". */
    private final class FavAdapter extends BaseAdapter {
        private final AppEntry[] slots = new AppEntry[Prefs.MAX_FAVORITES];
        private int rowHeight;

        void rebuild() {
            int n = 0;
            for (String k : favKeys) {
                AppEntry e = apps.resolve(k);
                if (e != null && n < slots.length) slots[n++] = e;
            }
            for (int i = n; i < slots.length; i++) slots[i] = null;
            notifyDataSetChanged();
        }

        void setRowHeight(int h) {
            if (h > 0 && h != rowHeight) { rowHeight = h; notifyDataSetChanged(); }
        }

        @Override public int getCount() { return slots.length; }
        @Override public AppEntry getItem(int pos) { return slots[pos]; }
        @Override public long getItemId(int pos) { return pos; }
        @Override public boolean hasStableIds() { return true; }

        @Override public View getView(final int pos, View convert, ViewGroup parent) {
            Holder h;
            if (convert == null) {
                convert = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fav, parent, false);
                h = new Holder();
                h.icon = convert.findViewById(R.id.icon);
                h.label = convert.findViewById(R.id.label);
                convert.setTag(h);
            } else {
                h = (Holder) convert.getTag();
            }
            if (rowHeight > 0) {
                ViewGroup.LayoutParams lp = convert.getLayoutParams();
                if (lp == null) lp = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight);
                if (lp.height != rowHeight) { lp.height = rowHeight; convert.setLayoutParams(lp); }
            }
            final AppEntry e = slots[pos];
            h.entry = e;
            if (e == null) {
                convert.setBackgroundResource(R.drawable.tile_empty_bg);
                h.icon.setTag(null);
                h.icon.setImageResource(R.drawable.ic_add);
                h.icon.setImageTintList(ColorStateList.valueOf(getColor(R.color.text_dim)));
                h.label.setText(R.string.empty_slot);
                h.label.setTextColor(getColor(R.color.text_dim));
                convert.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showApps(); } });
                convert.setOnLongClickListener(null);
            } else {
                convert.setBackgroundResource(R.drawable.tile_bg);
                h.icon.setImageTintList(null);
                h.label.setText(e.label);
                h.label.setTextColor(getColor(R.color.text));
                apps.bindIcon(e, h.icon, favIconPx);
                convert.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { apps.launch(HomeActivity.this, e); } });
                convert.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) { showAppMenu(e, true); return true; }
                });
            }
            return convert;
        }
    }

    private final class AppsAdapter extends BaseAdapter {
        private List<AppEntry> items = new ArrayList<>();

        void setItems(List<AppEntry> list) { items = list; notifyDataSetChanged(); }

        @Override public int getCount() { return items.size(); }
        @Override public AppEntry getItem(int pos) { return pos >= 0 && pos < items.size() ? items.get(pos) : null; }
        @Override public long getItemId(int pos) { return pos; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            Holder h;
            if (convert == null) {
                convert = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
                h = new Holder();
                h.icon = convert.findViewById(R.id.icon);
                h.label = convert.findViewById(R.id.label);
                convert.setTag(h);
            } else {
                h = (Holder) convert.getTag();
            }
            AppEntry e = items.get(pos);
            h.entry = e;
            h.label.setText(e.label);
            h.label.setAlpha(hidden.contains(e.key) ? 0.45f : 1f);
            h.icon.setAlpha(hidden.contains(e.key) ? 0.45f : 1f);
            apps.bindIcon(e, h.icon, appIconPx);
            return convert;
        }
    }
}
__EOF__

# ------------------------------------------------------------------ src: DiagnosticsActivity.java
cat > "$D/src/com/android/launcher3/DiagnosticsActivity.java" <<'__EOF__'
package com.android.launcher3;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.jancar.JancarBridge;

import java.io.File;
import java.io.FileWriter;
import java.util.Locale;

/** Informe en texto de memoria, servicios y eventos recientes. Pensado para medir en el equipo real. */
public final class DiagnosticsActivity extends Activity {
    private TextView report;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_diag);
        report = findViewById(R.id.report);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { refresh(); } });
        findViewById(R.id.btn_reconnect).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { LauncherApp.get().bridge().reconnect(); report.postDelayed(new Runnable() { @Override public void run() { refresh(); } }, 1500); }
        });
        findViewById(R.id.btn_gc).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { LauncherApp.get().apps().trimIcons(true); System.gc(); refresh(); }
        });
        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { save(); } });
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() { report.setText(buildReport()); }

    private String buildReport() {
        LauncherApp app = LauncherApp.get();
        JancarBridge bridge = app.bridge();
        StringBuilder sb = new StringBuilder(4096);
        sb.append("Launcher nativo ").append(BuildInfo.VERSION).append('\n');
        sb.append("Proceso activo: ").append(app.uptimeMs() / 1000).append(" s\n\n");

        sb.append("== Memoria ==\n");
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        sb.append(String.format(Locale.US, "Heap Java: %.1f MB usados / %.1f MB reservados (máx %.1f MB)\n",
                used / 1048576f, rt.totalMemory() / 1048576f, rt.maxMemory() / 1048576f));
        sb.append(String.format(Locale.US, "Heap nativo: %.1f MB\n", Debug.getNativeHeapAllocatedSize() / 1048576f));
        try {
            sb.append(String.format(Locale.US, "PSS total del proceso: %.1f MB\n", Debug.getPss() / 1024f));
        } catch (RuntimeException ignored) {}
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            sb.append(String.format(Locale.US, "Sistema: %.0f MB libres de %.0f MB%s (umbral %.0f MB)\n",
                    mi.availMem / 1048576f, mi.totalMem / 1048576f, mi.lowMemory ? " — MEMORIA BAJA" : "", mi.threshold / 1048576f));
            sb.append("Clase de memoria: ").append(am.getMemoryClass()).append(" MB (largeHeap ").append(am.getLargeMemoryClass()).append(" MB)\n");
        }
        sb.append("Caché de iconos: ").append(app.apps().getIconCacheCount()).append(" bitmaps, ")
                .append(app.apps().getIconCacheBytes() / 1024).append(" KB\n");
        sb.append("Apps lanzables: ").append(app.apps().getApps().size()).append(" (escaneos: ").append(app.apps().getRefreshCount()).append(")\n\n");

        sb.append("== Servicios com.jancar.services ==\n");
        sb.append(bridge.describeLinks()).append('\n');
        sb.append("ACC: ").append(bridge.isAccKnown() ? (bridge.isAccOn() ? "ON" : "OFF") : "desconocido")
                .append(" · cámara reversa: ").append(bridge.getCcdStatus()).append('\n');
        Boolean bl = bridge.isBacklightOn();
        sb.append("Retroiluminación: ").append(bl == null ? "?" : (bl ? "encendida" : "apagada")).append('\n');
        sb.append("App de memoria (servicio): ").append(String.valueOf(bridge.getMemoryAppPackage())).append('\n');
        sb.append("Último evento: ").append(bridge.getLastEvent()).append('\n');
        sb.append("Media: ").append(bridge.getMedia()).append("\n\n");

        sb.append("== Registro ==\n").append(Diag.dump());
        return sb.toString();
    }

    private void save() {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            File f = new File(dir, "launcher-diag.txt");
            FileWriter w = new FileWriter(f, false);
            w.write(buildReport());
            w.close();
            Toast.makeText(this, getString(R.string.diag_saved, f.getAbsolutePath()), Toast.LENGTH_LONG).show();
            Diag.log("diag", "informe guardado en " + f.getAbsolutePath());
        } catch (Exception e) {
            Toast.makeText(this, R.string.diag_save_failed, Toast.LENGTH_SHORT).show();
            Diag.warn("diag", "guardar", e);
        }
    }
}
__EOF__

# ------------------------------------------------------------------ src: jancar/MediaState.java
cat > "$D/src/com/android/launcher3/jancar/MediaState.java" <<'__EOF__'
package com.android.launcher3.jancar;

import android.graphics.Bitmap;

/** Instantánea de lo que el servicio del estéreo dice que suena. Solo se modifica en el hilo principal. */
public final class MediaState {
    // Tipos de fuente del servicio Jancar (IVIMedia.Type)
    public static final int TYPE_NONE = -1;
    public static final int TYPE_MUSIC = 0;
    public static final int TYPE_RADIO = 1;
    public static final int TYPE_VIDEO = 2;
    public static final int TYPE_A2DP = 7;
    public static final int TYPE_THIRD_PARTY = 20;

    // Estados (IVIMedia.MediaState)
    public static final int STATE_STOPPED = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_PLAYING = 3;

    public int type = TYPE_NONE;
    public int state = STATE_STOPPED;
    public String title = "";
    public String artist = "";
    public Bitmap cover;
    public int index;
    public int total;
    /** Posición y duración en segundos (así las publica el servicio). */
    public int position;
    public int duration;
    public long lastInfoAt;
    public long lastStateAt;

    public boolean isPlaying() { return state == STATE_PLAYING; }

    public boolean hasContent() { return type != TYPE_NONE && (title != null && !title.isEmpty()); }

    @Override public String toString() {
        return "type=" + type + " state=" + state + " title='" + title + "' artist='" + artist + "' pos=" + position + "/" + duration
                + " cover=" + (cover != null ? cover.getWidth() + "x" + cover.getHeight() : "no");
    }
}
__EOF__

# ------------------------------------------------------------------ src: jancar/JancarBridge.java
cat > "$D/src/com/android/launcher3/jancar/JancarBridge.java" <<'__EOF__'
package com.android.launcher3.jancar;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

import com.android.launcher3.Diag;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cliente binder propio (sin SDK del fabricante) para los servicios de com.jancar.services.
 *
 * Se conecta a tres servicios:
 *   - media  (IMedia):  título/artista/portada/estado + play-pausa/siguiente/anterior.
 *   - system (ISystem): openMemoryApp al arrancar y closeBackLight (apagar pantalla).
 *   - car    (ICar):    estado ACC y cámara de reversa.
 *
 * Reconexión: linkToDeath + onServiceDisconnected + broadcast "com.jancar.services.ready",
 * con reintentos espaciados (1, 2, 5, 10, 30 s). Todas las notificaciones a la UI van por el hilo principal.
 *
 * Códigos de transacción tomados de los stubs AIDL del APK de fábrica (ivi-launcher 2.0, servicios 3.0.0).
 */
public final class JancarBridge {

    public interface Listener {
        void onMediaChanged(MediaState state);
        void onServicesChanged();
        void onAccChanged(boolean on);
        void onCcdChanged(int status);
    }

    public static final String SERVICES_PACKAGE = "com.jancar.services";
    private static final String ACTION_MAIN = "com.jancar.services.action.main";
    private static final String ACTION_MEDIA = "com.jancar.services.action.media";
    private static final String ACTION_SYSTEM = "com.jancar.services.action.system";
    private static final String ACTION_CAR = "com.jancar.services.action.car";
    private static final String ACTION_READY = "com.jancar.services.ready";

    private static final String DESC_MEDIA = "com.jancar.services.media.IMedia";
    private static final String DESC_MEDIA_CB = "com.jancar.services.media.IMediaInfoCallback";
    private static final String DESC_SYSTEM = "com.jancar.services.system.ISystem";
    private static final String DESC_CAR = "com.jancar.services.car.ICar";
    private static final String DESC_CAR_CB = "com.jancar.services.car.ICarCallback";

    // IMedia
    private static final int MEDIA_REGISTER_INFO_CB = 5;
    private static final int MEDIA_UNREGISTER_INFO_CB = 6;
    private static final int MEDIA_GET_ACTIVE = 7;
    private static final int MEDIA_PLAY_PAUSE = 17;
    private static final int MEDIA_PREV = 18;
    private static final int MEDIA_NEXT = 19;
    private static final int MEDIA_REQUEST_INFO_AND_STATE = 22;
    // IMediaInfoCallback
    private static final int CB_ON_MEDIA_CHANGE = 1;
    private static final int CB_ON_PLAY_STATE_CHANGE = 2;
    private static final int CB_ON_MEDIA_ZONE_CHANGED = 3;
    private static final int CB_ON_SHOWN_TYPE_CHANGED = 4;
    // ISystem
    private static final int SYSTEM_CLOSE_BACKLIGHT = 10;
    private static final int SYSTEM_IS_OPEN_BACKLIGHT = 13;
    private static final int SYSTEM_OPEN_MEMORY_APP = 18;
    private static final int SYSTEM_GET_MEMORY_APP = 19;
    // ICar
    private static final int CAR_REGISTER_CB = 3;
    private static final int CAR_UNREGISTER_CB = 4;
    private static final int CAR_GET_CCD_STATUS = 10;
    private static final int CAR_IS_ACC_ON = 52;
    // ICarCallback
    private static final int CARCB_ON_ACC_CHANGED = 2;
    private static final int CARCB_ON_CCD_CHANGED = 3;
    private static final int CARCB_MAX_CODE = 32;

    private static final long[] RETRY_MS = {1000, 2000, 5000, 10000, 30000};
    private static final int MAX_COVER_SIDE = 1024;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final MediaState media = new MediaState();
    private final MediaInfoCallback mediaCb = new MediaInfoCallback();
    private final CarCallback carCb = new CarCallback();

    final Link mediaLink = new Link("media", ACTION_MEDIA);
    final Link systemLink = new Link("system", ACTION_SYSTEM);
    final Link carLink = new Link("car", ACTION_CAR);

    private boolean accOn = true;
    private boolean accKnown;
    private int ccdStatus;
    private boolean homeVisible;
    private boolean readyRegistered;
    private int lastCoverHash;
    private int lastCoverW, lastCoverH;
    private String lastEvent = "-";

    public JancarBridge(Context context) {
        app = context.getApplicationContext();
    }

    // ------------------------------------------------------------------ ciclo de vida

    public void start() {
        if (!readyRegistered) {
            readyRegistered = true;
            app.registerReceiver(readyReceiver, new IntentFilter(ACTION_READY));
        }
        connectAll();
    }

    public void connectAll() {
        mediaLink.connect();
        systemLink.connect();
        carLink.connect();
    }

    /** Reintento manual (pantalla de diagnóstico). */
    public void reconnect() {
        Diag.log("bridge", "reconexión manual");
        mediaLink.reset();
        systemLink.reset();
        carLink.reset();
        connectAll();
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    public void setHomeVisible(boolean visible) { homeVisible = visible; }

    public boolean isHomeVisible() { return homeVisible; }

    public MediaState getMedia() { return media; }

    public boolean isMediaOnline() { return mediaLink.binder != null; }

    public boolean isSystemOnline() { return systemLink.binder != null; }

    public boolean isCarOnline() { return carLink.binder != null; }

    public boolean isAccOn() { return accOn; }

    public boolean isAccKnown() { return accKnown; }

    public int getCcdStatus() { return ccdStatus; }

    public String getLastEvent() { return lastEvent; }

    public String describeLinks() {
        return mediaLink.describe() + "\n" + systemLink.describe() + "\n" + carLink.describe();
    }

    private final BroadcastReceiver readyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            Diag.log("bridge", "servicio listo (broadcast)");
            connectAll();
        }
    };

    // ------------------------------------------------------------------ enlace a un servicio

    final class Link implements ServiceConnection, IBinder.DeathRecipient {
        final String name;
        final String action;
        volatile IBinder binder;
        boolean bound;
        int attempt;
        int connections;
        long lastConnectedAt;
        private final Runnable retry = new Runnable() { @Override public void run() { connect(); } };

        Link(String name, String action) { this.name = name; this.action = action; }

        void connect() {
            if (binder != null) return;
            main.removeCallbacks(retry);
            Intent i = new Intent(action).setPackage(SERVICES_PACKAGE);
            // El SDK de fábrica arranca el servicio principal antes de enlazar; en segundo plano puede estar prohibido.
            try { app.startService(new Intent(ACTION_MAIN).setPackage(SERVICES_PACKAGE)); } catch (RuntimeException ignored) {}
            boolean ok;
            try {
                if (bound) { try { app.unbindService(this); } catch (RuntimeException ignored) {} bound = false; }
                ok = app.bindService(i, this, Context.BIND_AUTO_CREATE);
                bound = ok;
            } catch (RuntimeException e) {
                Diag.warn("bridge", name + " bindService", e);
                ok = false;
            }
            if (!ok) scheduleRetry();
        }

        void scheduleRetry() {
            long delay = RETRY_MS[Math.min(attempt, RETRY_MS.length - 1)];
            attempt++;
            Diag.log("bridge", name + " reintento en " + delay + " ms");
            main.removeCallbacks(retry);
            main.postDelayed(retry, delay);
        }

        void reset() {
            main.removeCallbacks(retry);
            IBinder b = binder;
            binder = null;
            if (b != null) { try { b.unlinkToDeath(this, 0); } catch (RuntimeException ignored) {} }
            if (bound) { try { app.unbindService(this); } catch (RuntimeException ignored) {} bound = false; }
            attempt = 0;
        }

        @Override public void onServiceConnected(ComponentName cn, IBinder b) {
            if (b == null) { scheduleRetry(); return; }
            try { b.linkToDeath(this, 0); } catch (RemoteException e) { scheduleRetry(); return; }
            binder = b;
            attempt = 0;
            connections++;
            lastConnectedAt = System.currentTimeMillis();
            Diag.log("bridge", name + " conectado");
            onLinked(this);
            notifyServices();
        }

        @Override public void onServiceDisconnected(ComponentName cn) {
            Diag.log("bridge", name + " desconectado");
            dropAndRetry();
        }

        @Override public void binderDied() {
            Diag.log("bridge", name + " murió");
            main.post(new Runnable() { @Override public void run() { dropAndRetry(); } });
        }

        private void dropAndRetry() {
            IBinder b = binder;
            binder = null;
            if (b != null) { try { b.unlinkToDeath(this, 0); } catch (RuntimeException ignored) {} }
            onUnlinked(this);
            notifyServices();
            scheduleRetry();
        }

        String describe() {
            return name + ": " + (binder != null ? "conectado" : "sin conexión") + " (conexiones=" + connections
                    + ", intento=" + attempt + (lastConnectedAt > 0 ? ", última=" + ((System.currentTimeMillis() - lastConnectedAt) / 1000) + " s" : "") + ")";
        }
    }

    private void onLinked(Link link) {
        if (link == mediaLink) {
            transactVoid(link.binder, DESC_MEDIA, MEDIA_REGISTER_INFO_CB, mediaCb);
            requestMediaState();
        } else if (link == carLink) {
            transactVoid(link.binder, DESC_CAR, CAR_REGISTER_CB, carCb);
            Boolean acc = transactBool(link.binder, DESC_CAR, CAR_IS_ACC_ON);
            if (acc != null) { accOn = acc; accKnown = true; }
            Integer ccd = transactInt(link.binder, DESC_CAR, CAR_GET_CCD_STATUS);
            if (ccd != null) ccdStatus = ccd;
            Diag.log("bridge", "acc=" + accOn + " ccd=" + ccdStatus);
        }
    }

    private void onUnlinked(Link link) {
        if (link == mediaLink) {
            // El servicio se cayó: lo que mostrábamos ya no es fiable.
            media.type = MediaState.TYPE_NONE;
            media.state = MediaState.STATE_STOPPED;
            media.title = "";
            media.artist = "";
            media.position = 0;
            media.duration = 0;
            replaceCover(null);
            notifyMedia();
        }
    }

    // ------------------------------------------------------------------ órdenes

    public void requestMediaState() {
        IBinder b = mediaLink.binder;
        if (b != null) transactVoid(b, DESC_MEDIA, MEDIA_REQUEST_INFO_AND_STATE, null);
    }

    public void mediaPlayPause() { simpleMedia(MEDIA_PLAY_PAUSE, "play/pausa"); }

    public void mediaNext() { simpleMedia(MEDIA_NEXT, "siguiente"); }

    public void mediaPrev() { simpleMedia(MEDIA_PREV, "anterior"); }

    private void simpleMedia(int code, String what) {
        IBinder b = mediaLink.binder;
        Diag.log("media", what + (b != null ? "" : " (sin servicio)"));
        if (b != null) transactVoid(b, DESC_MEDIA, code, null);
    }

    public int mediaGetActiveType() {
        IBinder b = mediaLink.binder;
        Integer v = b != null ? transactInt(b, DESC_MEDIA, MEDIA_GET_ACTIVE) : null;
        return v != null ? v : MediaState.TYPE_NONE;
    }

    /** Apaga la retroiluminación como lo hace el launcher de fábrica (closeBackLight(1)). */
    public boolean screenOff() {
        IBinder b = systemLink.binder;
        if (b == null) return false;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESC_SYSTEM);
            data.writeInt(1);
            b.transact(SYSTEM_CLOSE_BACKLIGHT, data, reply, 0);
            reply.readException();
            Diag.log("system", "closeBackLight(1)");
            return true;
        } catch (RemoteException | RuntimeException e) {
            Diag.warn("system", "closeBackLight", e);
            return false;
        } finally { data.recycle(); reply.recycle(); }
    }

    /** Pide al servicio que reabra la última app multimedia ("app de memoria"), como el launcher de fábrica 6 s tras arrancar. */
    public boolean openMemoryApp() {
        IBinder b = systemLink.binder;
        if (b == null) { Diag.log("system", "openMemoryApp: sin servicio"); return false; }
        String pkg = transactString(b, DESC_SYSTEM, SYSTEM_GET_MEMORY_APP);
        Diag.log("system", "openMemoryApp (memoria=" + pkg + ")");
        return transactVoid(b, DESC_SYSTEM, SYSTEM_OPEN_MEMORY_APP, null);
    }

    public String getMemoryAppPackage() {
        IBinder b = systemLink.binder;
        return b != null ? transactString(b, DESC_SYSTEM, SYSTEM_GET_MEMORY_APP) : null;
    }

    public Boolean isBacklightOn() {
        IBinder b = systemLink.binder;
        return b != null ? transactBool(b, DESC_SYSTEM, SYSTEM_IS_OPEN_BACKLIGHT) : null;
    }

    // ------------------------------------------------------------------ helpers binder

    private static boolean transactVoid(IBinder b, String desc, int code, IBinder strong) {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(desc);
            if (strong != null) data.writeStrongBinder(strong);
            b.transact(code, data, reply, 0);
            reply.readException();
            return true;
        } catch (RemoteException | RuntimeException e) {
            Diag.warn("bridge", desc + "#" + code, e);
            return false;
        } finally { data.recycle(); reply.recycle(); }
    }

    private static Integer transactInt(IBinder b, String desc, int code) {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(desc);
            b.transact(code, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (RemoteException | RuntimeException e) {
            Diag.warn("bridge", desc + "#" + code, e);
            return null;
        } finally { data.recycle(); reply.recycle(); }
    }

    private static Boolean transactBool(IBinder b, String desc, int code) {
        Integer v = transactInt(b, desc, code);
        return v == null ? null : v != 0;
    }

    private static String transactString(IBinder b, String desc, int code) {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(desc);
            b.transact(code, data, reply, 0);
            reply.readException();
            return reply.readString();
        } catch (RemoteException | RuntimeException e) {
            Diag.warn("bridge", desc + "#" + code, e);
            return null;
        } finally { data.recycle(); reply.recycle(); }
    }

    // ------------------------------------------------------------------ callbacks (hilo binder → principal)

    /** Stub de com.jancar.services.media.IMediaInfoCallback. */
    private final class MediaInfoCallback extends Binder implements IInterface {
        MediaInfoCallback() { attachInterface(this, DESC_MEDIA_CB); }

        @Override public IBinder asBinder() { return this; }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case CB_ON_MEDIA_CHANGE: {
                    data.enforceInterface(DESC_MEDIA_CB);
                    final int type = data.readInt();
                    final String title = data.readString();
                    final String artist = data.readString();
                    final int w = data.readInt();
                    final int h = data.readInt();
                    final byte[] pixels = data.createByteArray();
                    final int index = data.readInt();
                    final int total = data.readInt();
                    final boolean fav = data.readInt() != 0;
                    final Bitmap bmp = decodeCover(pixels, w, h);
                    main.post(new Runnable() {
                        @Override public void run() { applyMediaInfo(type, title, artist, bmp, index, total, pixels == null, fav); }
                    });
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                case CB_ON_PLAY_STATE_CHANGE: {
                    data.enforceInterface(DESC_MEDIA_CB);
                    final int type = data.readInt();
                    final int state = data.readInt();
                    final int position = data.readInt();
                    final int duration = data.readInt();
                    main.post(new Runnable() {
                        @Override public void run() { applyPlayState(type, state, position, duration); }
                    });
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                case CB_ON_MEDIA_ZONE_CHANGED: {
                    data.enforceInterface(DESC_MEDIA_CB);
                    data.readInt(); data.readInt();
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                case CB_ON_SHOWN_TYPE_CHANGED: {
                    data.enforceInterface(DESC_MEDIA_CB);
                    final int type = data.readInt();
                    main.post(new Runnable() { @Override public void run() { lastEvent = "shownType=" + type; } });
                    if (reply != null) reply.writeNoException();
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    /** Stub de com.jancar.services.car.ICarCallback: solo ACC y cámara de reversa; el resto se acepta y se ignora. */
    private final class CarCallback extends Binder implements IInterface {
        CarCallback() { attachInterface(this, DESC_CAR_CB); }

        @Override public IBinder asBinder() { return this; }

        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code < 1 || code > CARCB_MAX_CODE) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(DESC_CAR_CB);
            if (code == CARCB_ON_ACC_CHANGED) {
                final boolean on = data.readInt() != 0;
                main.post(new Runnable() {
                    @Override public void run() {
                        accOn = on; accKnown = true; lastEvent = "acc=" + on;
                        Diag.log("car", "ACC " + (on ? "ON" : "OFF"));
                        for (Listener l : new ArrayList<>(listeners)) l.onAccChanged(on);
                    }
                });
            } else if (code == CARCB_ON_CCD_CHANGED) {
                final int status = data.readInt();
                main.post(new Runnable() {
                    @Override public void run() {
                        ccdStatus = status; lastEvent = "ccd=" + status;
                        Diag.log("car", "CCD " + status);
                        for (Listener l : new ArrayList<>(listeners)) l.onCcdChanged(status);
                    }
                });
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    }

    // ------------------------------------------------------------------ estado multimedia

    private Bitmap decodeCover(byte[] pixels, int w, int h) {
        if (pixels == null || w <= 0 || h <= 0 || w > MAX_COVER_SIDE || h > MAX_COVER_SIDE) return null;
        int hash = Arrays.hashCode(pixels);
        synchronized (this) {
            if (hash == lastCoverHash && w == lastCoverW && h == lastCoverH && media.cover != null && !media.cover.isRecycled()) {
                return media.cover; // misma portada: no se vuelve a crear
            }
        }
        try {
            Bitmap bmp;
            long argb = (long) w * h * 4, rgb565 = (long) w * h * 2;
            if (pixels.length >= argb) {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                bmp.copyPixelsFromBuffer(ByteBuffer.wrap(pixels, 0, (int) argb));
            } else if (pixels.length >= rgb565) {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
                bmp.copyPixelsFromBuffer(ByteBuffer.wrap(pixels, 0, (int) rgb565));
            } else {
                return null;
            }
            synchronized (this) { lastCoverHash = hash; lastCoverW = w; lastCoverH = h; }
            return bmp;
        } catch (RuntimeException | OutOfMemoryError e) {
            Diag.warn("media", "portada " + w + "x" + h + " (" + pixels.length + " B)", e);
            return null;
        }
    }

    private void applyMediaInfo(int type, String title, String artist, Bitmap cover, int index, int total, boolean noPixels, boolean fav) {
        media.type = type;
        media.title = title != null ? title : "";
        media.artist = artist != null ? artist : "";
        media.index = index;
        media.total = total;
        media.lastInfoAt = System.currentTimeMillis();
        if (cover != media.cover) replaceCover(cover);
        lastEvent = "mediaChange type=" + type;
        Diag.log("media", "info " + media);
        notifyMedia();
    }

    private void applyPlayState(int type, int state, int position, int duration) {
        boolean changed = media.type != type || media.state != state;
        media.type = type;
        media.state = state;
        media.position = position;
        media.duration = duration;
        media.lastStateAt = System.currentTimeMillis();
        if (changed) { lastEvent = "playState type=" + type + " state=" + state; Diag.log("media", "estado " + state + " tipo " + type); }
        notifyMedia();
    }

    /** Sustituye la portada y recicla la anterior después de que la UI haya cambiado de bitmap. */
    private void replaceCover(Bitmap fresh) {
        final Bitmap old;
        synchronized (this) {
            old = media.cover;
            media.cover = fresh;
            if (fresh == null) { lastCoverHash = 0; lastCoverW = 0; lastCoverH = 0; }
        }
        if (old != null && old != fresh) {
            main.post(new Runnable() {
                @Override public void run() { if (!old.isRecycled()) old.recycle(); }
            });
        }
    }

    private void notifyMedia() {
        for (Listener l : new ArrayList<>(listeners)) l.onMediaChanged(media);
    }

    private void notifyServices() {
        for (Listener l : new ArrayList<>(listeners)) l.onServicesChanged();
    }
}
__EOF__

# ------------------------------------------------------------------ src: ui/ClockController.java
cat > "$D/src/com/android/launcher3/ui/ClockController.java" <<'__EOF__'
package com.android.launcher3.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.format.DateFormat;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Reloj y fecha. Se actualiza solo con ACTION_TIME_TICK (una vez por minuto, y solo mientras la
 * pantalla está en primer plano). Los formateadores se crean una vez y se reutilizan.
 */
public final class ClockController {
    private final Context ctx;
    private final TextView clock;
    private final TextView date;
    private final Date now = new Date();
    private SimpleDateFormat timeFmt;
    private SimpleDateFormat dateFmt;
    private boolean registered;

    public ClockController(Context ctx, TextView clock, TextView date) {
        this.ctx = ctx;
        this.clock = clock;
        this.date = date;
        rebuildFormats();
    }

    private void rebuildFormats() {
        Locale loc = Locale.getDefault();
        boolean h24 = DateFormat.is24HourFormat(ctx);
        timeFmt = new SimpleDateFormat(DateFormat.getBestDateTimePattern(loc, h24 ? "Hm" : "hm"), loc);
        dateFmt = new SimpleDateFormat(DateFormat.getBestDateTimePattern(loc, "EEEEdMMMM"), loc);
    }

    public void start() {
        if (!registered) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            f.addAction(Intent.ACTION_DATE_CHANGED);
            f.addAction(Intent.ACTION_LOCALE_CHANGED);
            ctx.registerReceiver(receiver, f);
            registered = true;
        }
        update();
    }

    public void stop() {
        if (registered) {
            try { ctx.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) {}
            registered = false;
        }
    }

    public void update() {
        now.setTime(System.currentTimeMillis());
        String t = timeFmt.format(now);
        String d = dateFmt.format(now);
        if (!d.isEmpty()) d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
        clock.setText(t);
        date.setText(d);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_TIMEZONE_CHANGED.equals(a) || Intent.ACTION_LOCALE_CHANGED.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a)) {
                rebuildFormats();
            }
            update();
        }
    };
}
__EOF__

# ------------------------------------------------------------------ src: ui/StatusController.java
cat > "$D/src/com/android/launcher3/ui/StatusController.java" <<'__EOF__'
package com.android.launcher3.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.Diag;
import com.android.launcher3.R;

import java.util.HashSet;
import java.util.Set;

/**
 * Iconos de Bluetooth y Wi-Fi. Sin sondeo: solo broadcasts y NetworkCallback, y solo mientras
 * la pantalla está en primer plano.
 *
 * Nota: la pila BT de estos equipos es propia (com.jancar.btservice); el estado "conectado" se
 * deduce de los broadcasts ACL del framework, igual que hacía el launcher de fábrica.
 */
public final class StatusController {
    private final Context ctx;
    private final ImageView bt;
    private final ImageView wifi;
    private final TextView text;
    private final int colorOn;
    private final int colorDim;
    private final Set<String> aclDevices = new HashSet<>();
    private boolean btEnabled;
    private boolean btConnected;
    private boolean wifiUp;
    private int wifiLevel = -1;
    private boolean registered;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback netCb;

    public StatusController(Context ctx, ImageView bt, ImageView wifi, TextView text) {
        this.ctx = ctx;
        this.bt = bt;
        this.wifi = wifi;
        this.text = text;
        colorOn = ctx.getColor(R.color.accent);
        colorDim = ctx.getColor(R.color.text_dim);
    }

    public void start() {
        if (registered) return;
        registered = true;
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(WifiManager.RSSI_CHANGED_ACTION);
        ctx.registerReceiver(receiver, f);

        cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { post(true); }
                @Override public void onLost(Network n) { post(false); }
                private void post(final boolean up) {
                    wifi.post(new Runnable() { @Override public void run() { wifiUp = up; if (up) readRssi(); render(); } });
                }
            };
            try {
                cm.registerNetworkCallback(new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), netCb);
            } catch (RuntimeException e) { Diag.warn("status", "registerNetworkCallback", e); netCb = null; }
        }
        readInitialBt();
        readRssi();
        render();
    }

    public void stop() {
        if (!registered) return;
        registered = false;
        try { ctx.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) {}
        if (cm != null && netCb != null) { try { cm.unregisterNetworkCallback(netCb); } catch (RuntimeException ignored) {} }
        netCb = null;
    }

    private void readInitialBt() {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        btEnabled = a != null && a.isEnabled();
        boolean profile = false;
        if (btEnabled) {
            try {
                profile = a.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED
                        || a.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED;
            } catch (RuntimeException ignored) {}
        }
        btConnected = profile || !aclDevices.isEmpty();
    }

    private void readRssi() {
        wifiLevel = -1;
        if (!wifiUp) return;
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
            if (info != null) wifiLevel = WifiManager.calculateSignalLevel(info.getRssi(), 4);
        } catch (RuntimeException ignored) {}
    }

    private void render() {
        bt.setVisibility(btEnabled ? View.VISIBLE : View.GONE);
        bt.setImageTintList(android.content.res.ColorStateList.valueOf(btConnected ? colorOn : colorDim));
        wifi.setVisibility(wifiUp ? View.VISIBLE : View.GONE);
        if (wifiUp) {
            wifi.setImageTintList(android.content.res.ColorStateList.valueOf(colorOn));
            wifi.setAlpha(wifiLevel < 0 ? 1f : 0.4f + 0.2f * wifiLevel);
        }
        if (btConnected) text.setText(R.string.bt_connected);
        else text.setText("");
    }

    public String describe() {
        return "bt enabled=" + btEnabled + " connected=" + btConnected + " acl=" + aclDevices.size()
                + " | wifi up=" + wifiUp + " level=" + wifiLevel;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            switch (a) {
                case BluetoothAdapter.ACTION_STATE_CHANGED: {
                    int st = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                    btEnabled = st == BluetoothAdapter.STATE_ON;
                    if (!btEnabled) { aclDevices.clear(); btConnected = false; }
                    break;
                }
                case BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED: {
                    int st = i.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED);
                    btConnected = st == BluetoothAdapter.STATE_CONNECTED || !aclDevices.isEmpty();
                    break;
                }
                case BluetoothDevice.ACTION_ACL_CONNECTED:
                case BluetoothDevice.ACTION_ACL_DISCONNECTED: {
                    BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String addr = d != null ? d.getAddress() : "?";
                    if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) aclDevices.add(addr); else aclDevices.remove(addr);
                    btConnected = !aclDevices.isEmpty();
                    Diag.log("status", a.substring(a.lastIndexOf('.') + 1) + " " + addr);
                    break;
                }
                case WifiManager.RSSI_CHANGED_ACTION:
                    readRssi();
                    break;
                default:
                    return;
            }
            render();
        }
    };
}
__EOF__

# ------------------------------------------------------------------ src: ui/MediaWidget.java
cat > "$D/src/com/android/launcher3/ui/MediaWidget.java" <<'__EOF__'
package com.android.launcher3.ui;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.launcher3.R;
import com.android.launcher3.jancar.MediaState;

/** Pinta el estado multimedia en la tarjeta. Sin temporizadores: solo redibuja cuando el servicio avisa. */
public final class MediaWidget {
    private final View card;
    private final ImageView cover;
    private final TextView source;
    private final TextView title;
    private final TextView artist;
    private final ProgressBar progress;
    private final TextView time;
    private final ImageButton play;
    private final int dimColor;
    private final StringBuilder sb = new StringBuilder(16);
    private Bitmap shownCover;
    private boolean lastPlaying;
    private int lastType = Integer.MIN_VALUE;
    private boolean lastOnline = true;

    public MediaWidget(View card) {
        this.card = card;
        cover = card.findViewById(R.id.media_cover);
        source = card.findViewById(R.id.media_source);
        title = card.findViewById(R.id.media_title);
        artist = card.findViewById(R.id.media_artist);
        progress = card.findViewById(R.id.media_progress);
        time = card.findViewById(R.id.media_time);
        play = card.findViewById(R.id.media_play);
        dimColor = card.getContext().getColor(R.color.text_dim);
        title.setSelected(true); // marquee
    }

    public void render(MediaState s, boolean online) {
        if (!online) {
            if (lastOnline) {
                source.setText(R.string.media_service_offline);
                title.setText(R.string.media_idle);
                artist.setText("");
                setCover(null);
                progress.setProgress(0);
                time.setText("");
                setPlaying(false);
                lastOnline = false;
            }
            return;
        }
        lastOnline = true;

        if (s.type != lastType) {
            lastType = s.type;
            source.setText(sourceLabel(s.type));
        }
        if (s.hasContent()) {
            title.setText(s.title);
            artist.setText(s.artist == null || s.artist.isEmpty() ? "" : s.artist);
        } else {
            title.setText(R.string.media_idle);
            artist.setText(R.string.media_open_hint);
        }
        setCover(s.cover);
        setPlaying(s.isPlaying());

        if (s.duration > 0 && s.type != MediaState.TYPE_RADIO) {
            progress.setVisibility(View.VISIBLE);
            time.setVisibility(View.VISIBLE);
            progress.setProgress((int) (1000L * Math.max(0, Math.min(s.position, s.duration)) / s.duration));
            sb.setLength(0);
            appendTime(sb, s.position).append(" / ");
            appendTime(sb, s.duration);
            time.setText(sb);
        } else {
            progress.setVisibility(View.INVISIBLE);
            time.setVisibility(View.INVISIBLE);
        }
    }

    private void setCover(Bitmap b) {
        if (b == shownCover && b != null) return;
        shownCover = b;
        if (b == null || b.isRecycled()) {
            cover.setImageResource(R.drawable.ic_music_note);
            cover.setImageTintList(ColorStateList.valueOf(dimColor));
            cover.setPadding(28, 28, 28, 28);
        } else {
            cover.setImageTintList(null);
            cover.setPadding(0, 0, 0, 0);
            cover.setImageBitmap(b);
        }
    }

    private void setPlaying(boolean playing) {
        if (playing == lastPlaying && play.getDrawable() != null) return;
        lastPlaying = playing;
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(card.getContext().getString(playing ? R.string.pause : R.string.play));
    }

    private static int sourceLabel(int type) {
        switch (type) {
            case MediaState.TYPE_MUSIC: return R.string.media_source_music;
            case MediaState.TYPE_RADIO: return R.string.media_source_radio;
            case MediaState.TYPE_VIDEO: return R.string.media_source_video;
            case MediaState.TYPE_A2DP: return R.string.media_source_bt;
            case MediaState.TYPE_NONE: return R.string.media_source_music;
            default: return R.string.media_source_other;
        }
    }

    private static StringBuilder appendTime(StringBuilder sb, int seconds) {
        if (seconds < 0) seconds = 0;
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) sb.append(h).append(':');
        if (h > 0 && m < 10) sb.append('0');
        sb.append(m).append(':');
        if (s < 10) sb.append('0');
        sb.append(s);
        return sb;
    }
}
__EOF__

echo "Proyecto creado en ./$D"
echo "Siguiente:  cd $D && ./tools/get-tools.sh && ./build.sh"
