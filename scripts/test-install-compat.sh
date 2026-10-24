#!/usr/bin/env bash
set -euo pipefail
OUT="${1:?artifact directory required}"
VERSION="${2:?version required}"
STANDALONE=io.github.madebycli.futon.mihonfix
LEGACY=io.github.landwarderer.futon
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
curl -fL --retry 3 'https://github.com/madebycli/Futon-MihonFIx/releases/download/v9.8.5/Futon-9.8.5.apk' -o "$WORK/previous.apk"
echo "d4deebca968a4d1696e653fed6d95b7a738e677cea5522963ee632ac41f3e586  $WORK/previous.apk" | sha256sum -c -
curl -fL --retry 3 'https://github.com/AppFuton/Futon/releases/download/v9.9/app-release.apk' -o "$WORK/upstream.apk"
adb install "$WORK/previous.apk"
adb install -r "$OUT/Futon-$VERSION-legacy-update.apk"
adb install "$OUT/Futon-MihonFix-$VERSION.apk"
adb install -r "$OUT/Futon-MihonFix-$VERSION.apk"
adb shell am force-stop "$LEGACY"
adb uninstall "$LEGACY"
adb install "$WORK/upstream.apk"
adb install -r "$OUT/Futon-MihonFix-$VERSION.apk"
adb logcat -c
adb shell am start -W -n "$STANDALONE/io.github.landwarderer.futon.main.ui.MainActivity"
sleep 5
adb shell pidof "$STANDALONE"
adb logcat -b crash -d > "$OUT/emulator-crash-log.txt"
if grep -Fq "Process: $STANDALONE," "$OUT/emulator-crash-log.txt"; then
  cat "$OUT/emulator-crash-log.txt"
  exit 1
fi
adb shell dumpsys package "$STANDALONE" > "$OUT/emulator-package.txt"
for IDENTITY in "$STANDALONE.history" "$STANDALONE.favourites" "$STANDALONE.files"; do
  grep -Fq "$IDENTITY" "$OUT/emulator-package.txt"
done
{
  echo "Android API: $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "ABI: $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "PASS: published repository-key 9.8.5 upgrades to new legacy release."
  echo "PASS: standalone installs beside legacy and updates in place."
  echo "PASS: standalone coexists with original upstream Futon 9.9."
  echo "PASS: standalone package-specific sync/file providers are registered."
  echo "PASS: standalone launches and its process remains alive without a crash."
  echo "Physical Galaxy S26 Ultra validation: PENDING."
} | tee "$OUT/INSTALL-VALIDATION.txt"
