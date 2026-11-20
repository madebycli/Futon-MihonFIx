#!/usr/bin/env bash
# Verifies the release APKs of Clio: the universal APK and one APK per ABI. Each must have the release package,
# version, SDK levels and certificate, carry the native libraries of its ABI (the universal one those of every ABI),
# and contain the Mihon extension host ABI after R8. Writes a .sha256 file next to each APK.
set -euo pipefail
OUT="${1:?artifact directory required}"
VERSION="${2:?version required}"
VERSION_CODE="${3:?version code required}"
APKSIGNER="$(find "$ANDROID_HOME/build-tools" -type f -name apksigner | sort -V | tail -n 1)"
AAPT="$(find "$ANDROID_HOME/build-tools" -type f -name aapt | sort -V | tail -n 1)"
CERT=12fe17245decb84c98985ddcc08884b874d10a78e4fb747a6ed3e2a42d77b625
PACKAGE=io.github.madebycli.futon.mihonfix
ABIS="arm64-v8a armeabi-v7a x86_64 x86"
for KIND in universal $ABIS; do
  APK="$OUT/Clio-$VERSION-$KIND.apk"
  test -s "$APK"
  SIGNATURE="$("$APKSIGNER" verify --verbose --print-certs "$APK")"
  printf '%s\n' "$SIGNATURE"
  grep -Fq "certificate SHA-256 digest: $CERT" <<< "$SIGNATURE"
  grep -Fq 'Verified using v2 scheme (APK Signature Scheme v2): true' <<< "$SIGNATURE"
  BADGING="$("$AAPT" dump badging "$APK")"
  IDENTITY="${BADGING%%$'\n'*}"
  echo "$IDENTITY"
  grep -Fq "package: name='$PACKAGE'" <<< "$IDENTITY"
  grep -Fq "versionCode='$VERSION_CODE'" <<< "$IDENTITY"
  grep -Fq "versionName='$VERSION'" <<< "$IDENTITY"
  grep -Fq "sdkVersion:'26'" <<< "$BADGING"
  grep -Fq "targetSdkVersion:'36'" <<< "$BADGING"
  unzip -Z1 "$APK" | grep '^lib/' | sort > "$OUT/$KIND-libraries.txt" || true
  if [ "$KIND" = universal ]; then
    for ABI in $ABIS; do
      grep -q "^lib/$ABI/" "$OUT/$KIND-libraries.txt"
    done
  else
    # Only the native code of its own ABI.
    grep -q "^lib/$KIND/" "$OUT/$KIND-libraries.txt"
    if grep -v "^lib/$KIND/" "$OUT/$KIND-libraries.txt" | grep -q .; then
      echo "::error::$KIND APK contains native code of another ABI"
      exit 1
    fi
  fi
  DEX_DIR="$(mktemp -d)"
  unzip -q "$APK" 'classes*.dex' -d "$DEX_DIR"
  cat "$DEX_DIR"/classes*.dex | strings > "$DEX_DIR/strings.txt"
  for SYMBOL in \
    'com/squareup/zstd/ZstdCompressor' \
    'androidx/preference/PreferenceScreen' \
    'eu/kanade/tachiyomi/source/ConfigurableSource' \
    'eu/kanade/tachiyomi/source/Source' \
    'eu/kanade/tachiyomi/source/model/RefreshContext' \
    'eu/kanade/tachiyomi/source/online/HttpSource' \
    'eu/kanade/tachiyomi/source/model/SManga' \
    'eu/kanade/tachiyomi/source/model/SChapter' \
    'eu/kanade/tachiyomi/source/model/SMangaUpdate' \
    'eu/kanade/tachiyomi/source/model/Page' \
    'getMangaUpdate' 'fetchRelatedMangaList' 'getChapterList' 'getPageList' 'fetchPageList' \
    'okhttp3/brotli/Brotli' 'CaptchaAutoResolveCoordinator' 'TachiyomiApkClassLoaderPolicy' 'org/brotli/dec/BrotliInputStream'; do
    if ! grep -Fq "$SYMBOL" "$DEX_DIR/strings.txt"; then
      echo "::error::Missing optimized Mihon ABI symbol: $SYMBOL ($KIND)"
      exit 1
    fi
  done
  rm -rf "$DEX_DIR"
  (cd "$OUT" && sha256sum "$(basename "$APK")" > "$(basename "$APK").sha256")
done
echo "All release APKs passed package, version, native library, Mihon ABI and stable-certificate verification."
