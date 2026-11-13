#!/usr/bin/env bash
# Pruebas de la lógica pura (sin Android): javac + java.
set -euo pipefail
cd "$(dirname "$0")/.."
out=build/test; rm -rf "$out"; mkdir -p "$out"
javac -encoding UTF-8 -d "$out" src/com/android/launcher3/car/Trip.java src/com/android/launcher3/car/SpeedLimit.java src/com/android/launcher3/car/FuelLog.java test/TripTest.java
java -cp "$out" TripTest
