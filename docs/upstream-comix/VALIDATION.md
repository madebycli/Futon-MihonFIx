# Validation evidence

## Upstream intermediate
- Source fa7907fe85ca1b07602e96c3d7c0f9666c64b09b.
- Run https://github.com/madebycli/Futon-MihonFIx/actions/runs/38360422794.
- Downloaded XML artifact 11794816908: 56 tests, 0 failures, 0 errors.
- Earlier failed SDK setup and JVM Android logging fixture were corrected; failures are not reported as passes.

## Comix implementation
- Source f66145ddd8a3b4ad47bd42ee1696a94fea0e7a35.
- Run https://github.com/madebycli/Futon-MihonFIx/actions/runs/38361172927.
- Downloaded XML artifact 11794424610: 69 tests, 0 failures, 0 errors (13 new Comix regressions).
- Debug APK workflow https://github.com/madebycli/Futon-MihonFIx/actions/runs/38361172910: success.
- Covered: distinct 227 chapters across three groups; duplicate uploads unchanged; extension-dedup output;
  uncertain/missing/invalid numbers, decimals, prologue, volume disambiguation; restored Source-ID;
  name collisions/wrong runtime package; empty/10,000 chapters; reader start/original URL;
  downloaded/read/current flags across groups; refresh reevaluation; other-source group behavior.
- Projection is pure and cannot issue network calls. Existing chip selection only updates StateFlow.
- Manual force refresh bypasses details cache; extension's own list memo invalidates changed dedup settings.

## Signed optimized feature build
Run https://github.com/madebycli/Futon-MihonFIx/actions/runs/38361172927 completed successfully.
Signed optimized APK job: 115178853979; assembleRelease successful, optimized ABI gate successful,
signature verification successful. Artifact 11796045970 (18,385,338 compressed bytes).
Certificate matches published 9.8.3 exactly. This temporary lane retains its historical test version label;
public 9.8.4 is built separately from e754d831497aa40c2d9083dfd40bbb79987203de.

## Published signed release 9.8.4
Published 2026-10-13T07:34:58Z:
https://github.com/madebycli/Futon-MihonFIx/releases/tag/v9.8.4
Source e754d831497aa40c2d9083dfd40bbb79987203de, release run 38361962248 SUCCESS:
https://github.com/madebycli/Futon-MihonFIx/actions/runs/38361962248
Fresh focused regression test step passed, optimized assembleRelease passed, DEX ABI gate passed,
APK identity io.github.landwarderer.futon / 9.8.4 / 90804 verified, v2 signature verified.
APK asset 636270858, 21,276,621 bytes; public SHA256:
0fd0249e2e56a5dcff4995b8c9799f91b671c82f387c557479b5f6f02ec95cca.
Release lint still reports the same 20 errors/408 warnings as the feature run.
Devel remains 6f171b4f9f07ba792d02e1682da779ac739002eb; PR #3 remains unmerged.
Expected certificate SHA256 from published 9.8.3 run 33611369866:
12fe17245decb84c98985ddcc08884b874d10a78e4fb747a6ed3e2a42d77b625.
The older AppValidator/CI.md fingerprint differs; it was not adopted as signing evidence.
Release lint remains diagnostic as in the base workflow. Downloaded report artifact 11794988842:
20 errors and 408 warnings. All error locations are in unmodified source/resource files;
comparison against changed production-line ranges found zero lint issues on changed lines.
This is not a clean full-project lint pass.

## Real-device retest: PENDING
Install the signed 9.8.4 APK over 9.8.3. Refresh the Comix manga from the screenshots.
With each chapter number present once, verify the groups disappear and chapters 1 through the newest
chapter form one list, including Asura/Saucerer entries. Open chapters across scanlator boundaries,
continue from existing history, verify downloaded chapters and reversed ordering, then restart the app.
Disable/enable Comix Deduplicate Chapters and refresh: actual duplicates must keep original groups.
Verify another Mihon source and a native source retain their group behavior.
No hardware/emulator was available; unit tests do not prove on-device extension behavior.
