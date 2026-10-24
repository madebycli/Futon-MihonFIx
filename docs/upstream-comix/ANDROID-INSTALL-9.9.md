# Android installation conflict and AppFuton 9.9 integration (2026-10-24)

Reported device: Samsung Galaxy S26 Ultra. Obtainium shows `failureConflict` for repository 9.8.5.
User confirms that the development APK installs. This supports a release identity/signature collision;
the screenshot does not expose the exact PackageManager failure code. No physical device is attached.

The published legacy APK shares package `io.github.landwarderer.futon` with original AppFuton but uses
the repository certificate. Keep that package for updates and add an opt-in standalone production build
(`-Pstandalone=true`) using `io.github.madebycli.futon.mihonfix`. Derive both sync authorities and the
account type from the selected application ID. Existing file/search/AndroidX providers already use the ID.
Debug/nightly resource overrides and namespace/source packages remain compatible. No new key is generated.

Actual upstream delta since 5d2206a:
- 14809e041e82ce1dffb2fac6344f03e104e52e4a: novels, local PDF/EPUB, onboarding and watermark.
- 9162c49a380029365cc53283daa766c1f78b6e5d: upstream 9.9 release.
Both original commits are preserved as ancestors of this integration commit.

Conflict decisions: retain minSdk 26, existing modern/legacy Mihon ABI, zstd/brotli, classloader,
snapshot persistence, image-request ownership and Comix projection. Add upstream novel routing and
image guards to downstream repositories. Combine the Cloudflare challenge detection with existing
request replay metadata. Keep two Gradle workers for runner memory. Parser pin is dc223c3d80.
Upstream removes its obsolete Brotli stub; retain the downstream real Brotli decompression adapter
because modern Keiyoushi extensions use it with the host OkHttp compression API. Existing release versioning becomes 9.9.0/90900.

Kototoro reference inspected at a21960147267df273c7c44db584db9e47486c8a4, including its
TachiyomiApkClassLoaderPolicy and AppValidator. No speculative extension ABI port is required for
an Android package installation conflict. Upstream AppFuton novel code is integrated without replacing
the established downstream extension layer.

Cloud executor failed to start with permission_denied. Changes use GitHub Git-data APIs; GitHub Actions
is the execution environment for validation. Release workflow requires debug/instrumented compilation,
focused regressions including new upstream novel tests, two optimized builds, stable certificate and ABI
checks, and Android 16 emulator legacy upgrade plus coexistence with original Futon.
Publication is gated on these results. Lint remains diagnostic. Device test remains PENDING.
Inline Discord release notifications are removed from this lane; no external notification was requested.

## Verified signed 9.9.0 publication (2026-10-24)

Release: https://github.com/madebycli/Futon-MihonFIx/releases/tag/v9.9.0
Source: 7ca49784847411ec11e25a6829d97eee420ec145 (two-parent upstream integration).
Release run 40125428415 SUCCESS, job 120965704916; published 2026-10-24T19:50:19Z.
https://github.com/madebycli/Futon-MihonFIx/actions/runs/40125428415

Focused Mihon/Comix/reader and upstream novel tests passed; debug APK and instrumented-source compilation passed.
Both optimized R8 APKs passed package/version/minSdk 26/targetSdk 36, ARM64-library presence, host ABI and
v2 certificate checks. Certificate remains 12fe17245decb84c98985ddcc08884b874d10a78e4fb747a6ed3e2a42d77b625.
Keiyoushi extensions-lib main remains 42255ee5fa96d9425697b7c143587483207308d1; no new host ABI delta.

Android 16/API 36 x86_64 emulator passed:
- Update published repository-key 9.8.5 to the new legacy APK.
- Install/update standalone beside legacy.
- Install original AppFuton 9.9 and retain/update standalone alongside it.
- Confirm package-specific history/favourites/file providers.
- Launch standalone; process stays alive with no crash.

Standalone APK asset 673157154, 25,385,272 bytes:
Futon-MihonFix-9.9.0.apk
SHA256 ad8265207967d9214b315f4c5851eb4f10e5c3e3f47d1dba725c634a8807026d
Legacy APK asset 673157151, 25,385,940 bytes:
Futon-9.9.0-legacy-update.apk
SHA256 a244f99cae1f997dffc263842e9f8b90a7c0fc5b3e3d4191e7c51908692aa353

Regression XML artifact 12572786951; APK/install evidence artifact 12573693104.
Lint artifact 12573132063 reports 20 errors/457 warnings and remains diagnostic; this is not a clean lint pass.
Public assets include APK checksum files, BUILD-INFO.txt and INSTALL-VALIDATION.txt.

User confirmed a development build installed, then removed the penultimate development version.
The exact physical-device PackageManager conflict code remains unavailable.
No physical Galaxy S26 Ultra test has been performed; user installation retest remains PENDING.
Default branch devel is synchronized with this verified application/build/test tree;
the synchronization adds documentation evidence and uses [skip ci] to avoid duplicate builds/notifications.
