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

Conflict decisions: retain minSdk26, existing modern/legacy Mihon ABI, zstd/brotli, classloader,
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
checks, and Android16 emulator legacy upgrade plus coexistence with original Futon.
Publication is gated on these results. Lint remains diagnostic. Device test remains PENDING.
Inline Discord release notifications are removed from this lane; no external notification was requested.
