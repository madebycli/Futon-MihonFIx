#!/usr/bin/env bash
set -euo pipefail
OUT="$1"
VERSION="$2"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
STANDALONE=io.github.madebycli.futon.mihonfix
LEGACY=io.github.landwarderer.futon
adb uninstall "$STANDALONE"
adb uninstall "$LEGACY"
curl -fL --retry 3 'https://github.com/madebycli/Futon-MihonFIx/releases/download/v9.10.0/Futon-MihonFix-9.10.0.apk' -o "$WORK/standalone.apk"
curl -fL --retry 3 'https://github.com/madebycli/Futon-MihonFIx/releases/download/v9.10.0/Futon-9.10.0-legacy-update.apk' -o "$WORK/legacy.apk"
(cd "$WORK" && sha256sum -c <<'CHECKSUMS'
b74461d404ad32c2014be63a642c586a46afb77f3811125975a0f212601bf2c4  standalone.apk
75183c3ad5b3f2c6603982a99045c0d41b54ac82d6a71e67908bffdacd3dd656  legacy.apk
CHECKSUMS
)
adb install "$WORK/standalone.apk"
adb install "$WORK/legacy.apk"
adb install -r "$OUT/Futon-MihonFix-$VERSION.apk"
adb install -r "$OUT/Futon-$VERSION-legacy-update.apk"
for PACKAGE in "$STANDALONE" "$LEGACY"; do
  adb shell dumpsys package "$PACKAGE" | tr -d '\r' | awk -v version="$VERSION" '$0 ~ "^[[:space:]]*versionName=" && $1 == "versionName=" version {found=1} END {exit !found}'
  adb logcat -c
  adb shell am start -W -n "$PACKAGE/io.github.landwarderer.futon.main.ui.MainActivity"
  adb shell pidof "$PACKAGE"
  adb logcat -b crash -d > "$WORK/crashes.txt"
  if grep -Fq "Process: $PACKAGE," "$WORK/crashes.txt"; then
    cat "$WORK/crashes.txt"
    exit 1
  fi
  adb shell am force-stop "$PACKAGE"
  echo "PASS: signed Clio 9.10.0 updates in place to the preview and launches ($PACKAGE)." | tee -a "$OUT/INSTALL-VALIDATION.txt"
done
