#!/usr/bin/env bash
# Prueba de arranque en un emulador (Android 8.1, API 27): instala el APK como pantalla de inicio,
# lo abre sin permiso de ubicación y con él, y guarda capturas, logcat y estado de actividades en emu-out/.
set -x
APK=${1:-out/Laucher-8227L.apk}
PKG=com.webgdl.launcher8227
HOME_ACT=$PKG/com.android.launcher3.HomeActivity
O=emu-out; mkdir -p $O
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb logcat -c
adb install -r "$APK" | tee $O/install.txt

shot() { adb exec-out screencap -p > "$O/$1.png"; adb shell dumpsys activity activities | grep -E "mResumedActivity|mFocusedActivity|Run #" > "$O/$1-actividades.txt"; adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" >> "$O/$1-actividades.txt"; }

# 1) Como lo hace el usuario: pulsar Inicio y elegir en «Abrir con… una vez / siempre»
adb shell input keyevent KEYCODE_HOME; sleep 6; shot 1-selector
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml $O/1-selector.xml >/dev/null 2>&1
# tocar «Laucher 8227L» y luego «Siempre» si aparecen
python3 - "$O/1-selector.xml" > $O/taps.txt <<'PY'
import re, sys
try: x = open(sys.argv[1], encoding='utf-8').read()
except Exception: sys.exit()
def center(label):
    m = re.search(r'text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(label), x)
    if m: a, b, c, d = map(int, m.groups()); print((a + c) // 2, (b + d) // 2)
center("Laucher 8227L")
PY
if [ -s $O/taps.txt ]; then adb shell input tap $(head -1 $O/taps.txt); sleep 2; fi
adb shell uiautomator dump /sdcard/ui2.xml >/dev/null 2>&1; adb pull /sdcard/ui2.xml $O/1b.xml >/dev/null 2>&1
B=$(python3 - "$O/1b.xml" <<'PY'
import re, sys
try: x = open(sys.argv[1], encoding='utf-8').read()
except Exception: sys.exit()
for lab in ("ALWAYS", "Always", "SIEMPRE", "Siempre"):
    m = re.search(r'text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % lab, x)
    if m: a, b, c, d = map(int, m.groups()); print((a + c) // 2, (b + d) // 2); break
PY
)
[ -n "$B" ] && adb shell input tap $B
sleep 12; shot 2-tras-elegir
adb shell cmd package set-home-activity $HOME_ACT || true

# 2) Sin permiso: abrir el inicio
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME | tee $O/3-start.txt
sleep 10; shot 3-inicio-sin-permiso

# 3) Con permiso de ubicación (como quien lo concedió en la 3.5.0) y el proceso reiniciado
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell am force-stop $PKG
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME | tee $O/4-start.txt
sleep 15; shot 4-inicio-con-permiso
adb emu geo fix -103.35 20.67 2>/dev/null; sleep 3; adb emu geo fix -103.349 20.671 2>/dev/null; sleep 5; shot 5-con-gps

adb shell dumpsys activity services $PKG > $O/servicios.txt
adb shell dumpsys notification > $O/notificaciones.txt
adb shell pidof $PKG > $O/pid.txt
adb shell run-as $PKG cat files/crash.txt > $O/crash.txt 2>&1 || true
adb shell cat /sdcard/Android/data/$PKG/files/crash.txt >> $O/crash.txt 2>&1 || true
adb shell ls /data/anr >> $O/anr.txt 2>&1 || true
adb logcat -d -v time > $O/logcat.txt
grep -nE "FATAL|AndroidRuntime|ANR in|Exception|launcher8227|art  :" $O/logcat.txt | head -300 > $O/logcat-resumen.txt
set +x
for f in $O/*actividades.txt $O/3-start.txt $O/4-start.txt $O/pid.txt $O/crash.txt $O/anr.txt $O/logcat-resumen.txt; do echo "===== $f"; cat "$f"; done
exit 0
