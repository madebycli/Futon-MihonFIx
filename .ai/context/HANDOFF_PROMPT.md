# Futon handoff: signed 9.9.0 and Android installation conflict, 2026-10-24

Read .ai/CONTEXT.md and its validated central route, then STATE.md, graph.yaml and
docs/upstream-comix/ANDROID-INSTALL-9.9.md. Repo madebycli/Futon-MihonFIx (ID1345465394), default devel.

The user authorized actual upstream integration, the Galaxy S26 Ultra installation fix and a new signed release.
Both missing AppFuton commits through 9162c49a380029365cc53283daa766c1f78b6e5d are preserved by
two-parent source 7ca49784847411ec11e25a6829d97eee420ec145. Upstream 9.9 novel/PDF/EPUB/onboarding
features and parser dc223c3d80 coexist with existing Mihon/Comix compatibility. Retain the downstream
real Brotli adapter, minSdk 26, classloader/interceptor contracts, image ownership and snapshots.

Signed 9.9.0/90900 is PUBLISHED, release run 40125428415 SUCCESS. Both optimized APKs retain the
repository certificate 12fe17245decb84c98985ddcc08884b874d10a78e4fb747a6ed3e2a42d77b625.
Standalone Futon-MihonFix-9.9.0.apk uses io.github.madebycli.futon.mihonfix and separate sync identities.
Legacy-update APK keeps io.github.landwarderer.futon for in-place upgrades of repository-key installs.
Keep BOTH package identities and the same signing key stable in future releases. -Pstandalone=true selects
the separate app; the release workflow builds both. Namespace/class names stay unchanged.
Default devel is synchronized to the verified app/build/test tree, with documentation-only evidence changes.

Android 16/API 36 emulator passed legacy 9.8.5 upgrade, coexistence with original AppFuton 9.9,
standalone replacement, provider registration and startup. Focused regressions and debug/instrumented
compilation passed. Lint remains diagnostic: 20 errors/457 warnings. Public release has checksum/build/install proof.
Standalone asset 673157154, SHA256 ad8265207967d9214b315f4c5851eb4f10e5c3e3f47d1dba725c634a8807026d.
Legacy asset 673157151, SHA256 a244f99cae1f997dffc263842e9f8b90a7c0fc5b3e3d4191e7c51908692aa353.

Device evidence: Obtainium failureConflict on 9.8.5; dev APK installed, then the user removed the penultimate dev build.
Exact device-side PackageManager reason unavailable. A physical Galaxy S26 Ultra retest remains PENDING.
No Play Protect bypass or new signing key. Give the user the standalone APK for the next install attempt.
