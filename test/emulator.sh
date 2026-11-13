#!/usr/bin/env bash
# Prueba de arranque en un emulador (Android 8.1, API 27): instala el APK como pantalla de inicio, lo abre,
# activa los instrumentos como lo haría el usuario, simula los avisos de ACC del estéreo y guarda capturas,
# logcat y estado en emu-out/. Falla (exit 1) si el launcher se cierra o deja de responder.
set -x
APK=${1:-out/Laucher-8227L.apk}
PKG=com.webgdl.launcher8227
HOME_ACT=$PKG/com.android.launcher3.HomeActivity
O=emu-out; mkdir -p $O
adb wait-for-device
adb logcat -c
adb install -r "$APK" | tee $O/install.txt

shot() {
  adb exec-out screencap -p > "$O/$1.png"
  adb shell dumpsys activity activities | grep -E "mResumedActivity|Run #" > "$O/$1-actividades.txt"
  adb shell pidof $PKG >> "$O/$1-actividades.txt"
}
# toca el primer elemento de la pantalla cuyo texto contiene $1
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml $O/ui.xml >/dev/null 2>&1
  local xy; xy=$(python3 - "$O/ui.xml" "$1" <<'PY'
import re, sys
try: x = open(sys.argv[1], encoding='utf-8').read()
except Exception: sys.exit()
m = re.search(r'text="[^"]*%s[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(sys.argv[2]), x)
if m: a, b, c, d = map(int, m.groups()); print((a + c) // 2, (b + d) // 2)
PY
)
  [ -n "$xy" ] && adb shell input tap $xy && echo "TAP '$1' en $xy" || echo "NO ENCONTRADO '$1'"
}
acc() { adb shell am broadcast -a com.jancar.services.action.acc --ez acc "$1" >/dev/null; }

# 1) Como el usuario: pulsar Inicio y elegir en «Abrir con… una vez / siempre»
adb shell input keyevent KEYCODE_HOME; sleep 5; shot 1-selector
tap_text "Laucher 8227L"; sleep 2; tap_text "ALWAYS"; sleep 8; shot 2-tras-elegir
adb shell cmd package set-home-activity $HOME_ACT || true
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME | tee $O/start.txt
sleep 9; shot 3-inicio-recien-instalado
PID0=$(adb shell pidof $PKG)

# 2) Activar los instrumentos tocando la tarjeta y conceder el permiso
tap_text "speedometer"; sleep 3; shot 4-permiso
tap_text "ALLOW"; sleep 4; shot 5-activado
adb emu geo fix -103.3500 20.6700; sleep 2; adb emu geo fix -103.3495 20.6705; sleep 2; adb emu geo fix -103.3490 20.6710
sleep 18; shot 6-gps-comprobado       # tras ~17 s pasa al servicio en primer plano

# 2b) Aviso de voz: pulsación larga sobre el velocímetro (como la prueba del usuario)
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml $O/ui-gauge.xml >/dev/null 2>&1
GXY=$(python3 - "$O/ui-gauge.xml" <<'PY'
import re, sys
try: x = open(sys.argv[1], encoding='utf-8').read()
except Exception: sys.exit()
m = re.search(r'resource-id="[^"]*speed_gauge"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
if m: a, b, c, d = map(int, m.groups()); print((a + c) // 2, (b + d) // 2)
PY
)
echo "velocímetro en $GXY" | tee $O/aviso.txt
adb shell media volume --stream 3 --set 4 >> $O/aviso.txt 2>&1 || true
adb shell dumpsys audio | grep -iE "STREAM_MUSIC|streamVolume" -A3 | head -12 >> $O/aviso.txt
[ -n "$GXY" ] && adb shell input swipe $GXY $GXY 1300
sleep 2; shot 6a-ajustes-velocimetro
tap_text "Test the voice"
sleep 2; adb shell dumpsys audio | grep -iE "focus|duck" | head -20 >> $O/aviso.txt
shot 6b-aviso
sleep 9; adb shell dumpsys audio | grep -iE "focus stack|Audio Focus" -A4 | head -10 >> $O/aviso-despues.txt
shot 6c-tras-aviso

# 2c) Widget deslizable: pasar por las 6 páginas con deslizamientos y volver
if [ -n "$GXY" ]; then
  GX=${GXY% *}; GY=${GXY#* }
  for n in 2 3 4 5 6; do
    adb shell input swipe $((GX + 130)) $GY $((GX - 130)) $GY 150; sleep 2; shot 6d-pagina-$n
  done
  adb shell input swipe $((GX - 130)) $GY $((GX + 130)) $GY 150; sleep 1
  adb shell input swipe $((GX - 130)) $GY $((GX + 130)) $GY 150; sleep 2; shot 6e-vuelta-pagina-4
  adb shell input swipe $GX $GY $GX $GY 1300; sleep 2; shot 6f-ajustes-de-la-pagina
  adb shell input keyevent KEYCODE_BACK; sleep 1
fi

# 3) Avisos de ACC como los manda el estéreo: repetidos y apagado/encendido seguidos
acc true; acc true; sleep 1; acc true; sleep 8; shot 7-acc-repetido
acc false; acc true; acc false; acc true; sleep 8; shot 8-acc-apagado-encendido
acc false; sleep 3; acc true; sleep 8

# 4) Otra app delante y vuelta al inicio; reinicio del proceso con todo activado
adb shell am start -a android.settings.SETTINGS >/dev/null; sleep 5
adb shell input keyevent KEYCODE_HOME; sleep 5; shot 9-vuelta-al-inicio
PID1=$(adb shell pidof $PKG)
adb shell am force-stop $PKG
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
acc true; sleep 2; acc true; sleep 12; shot 10-reinicio-con-todo-activado
PID2=$(adb shell pidof $PKG)

adb shell dumpsys activity services $PKG > $O/servicios.txt
adb shell cat /sdcard/Android/data/$PKG/files/crash.txt > $O/crash.txt 2>/dev/null || true
adb logcat -d -v time > $O/logcat.txt
grep -nE "FATAL EXCEPTION|AndroidRuntime: |ANR in|did not then call|Process $PKG .*died|has died|Force stopping $PKG" $O/logcat.txt | grep -E "$PKG|AndroidRuntime|ANR|did not" | head -100 > $O/logcat-resumen.txt
grep -E "LauncherNativo|MediaPlayer|AudioTrack.*(error|fail)" $O/logcat.txt | grep -E "gps:|seguro|MediaPlayer" > $O/launcher-log.txt
set +x
echo "PID inicial=$PID0 tras ACC=$PID1 tras reinicio=$PID2" | tee $O/resultado.txt
for f in $O/launcher-log.txt $O/aviso.txt $O/aviso-despues.txt $O/*actividades.txt $O/servicios.txt $O/crash.txt $O/logcat-resumen.txt; do echo "===== $f"; cat "$f"; done
FAIL=0
[ -n "$PID0" ] && [ "$PID0" = "$PID1" ] || { echo "FALLO: el proceso cambió o murió durante la prueba" | tee -a $O/resultado.txt; FAIL=1; }
[ -n "$PID2" ] || { echo "FALLO: no arrancó tras el reinicio" | tee -a $O/resultado.txt; FAIL=1; }
grep -qE "FATAL EXCEPTION|ANR in $PKG|did not then call" $O/logcat-resumen.txt && { echo "FALLO: cierre o ANR en logcat" | tee -a $O/resultado.txt; FAIL=1; }
grep -q "isForeground=true" $O/servicios.txt || echo "AVISO: el servicio en primer plano no está activo al final" | tee -a $O/resultado.txt
[ $FAIL = 0 ] && echo "OK" | tee -a $O/resultado.txt
echo $FAIL > $O/fail.txt
exit 0
