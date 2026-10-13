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
At publication, devel was 6f171b4f9f07ba792d02e1682da779ac739002eb and PR #3 was unmerged.
PR #3 was subsequently merged; actual parent integration followed in PR #4 (see below).
Expected certificate SHA256 from published 9.8.3 run 33611369866:
12fe17245decb84c98985ddcc08884b874d10a78e4fb747a6ed3e2a42d77b625.
The older AppValidator/CI.md fingerprint differs; it was not adopted as signing evidence.
Release lint remains diagnostic as in the base workflow. Downloaded report artifact 11794988842:
20 errors and 408 warnings. All error locations are in unmodified source/resource files;
comparison against changed production-line ranges found zero lint issues on changed lines.
This is not a clean full-project lint pass.

## Real-device retest: PENDING
Install the latest signed 9.8.5 APK over 9.8.4. Refresh the Comix manga from the screenshots.
With each chapter number present once, verify the groups disappear and chapters 1 through the newest
chapter form one list, including Asura/Saucerer entries. Open chapters across scanlator boundaries,
continue from existing history, verify downloaded chapters and reversed ordering, then restart the app.
Disable/enable Comix Deduplicate Chapters and refresh: actual duplicates must keep original groups.
Verify another Mihon source and a native source retain their group behavior.
No hardware/emulator was available; unit tests do not prove on-device extension behavior.

## Actual AppFuton parent integration

All six original parent commits through 5d2206a6926adf53be0867ad784948ed16663f44 are ancestors of
devel after PR #4 merge 2c708fcf593279243d33650d8d0eae41d00a5bad. GitHub compare behind_by=0.
Debug run38436109198 passed, including compileDebugAndroidTestKotlin. Instrumented runtime remains PENDING.
Mihon/reader run38436109226 passed. Downloaded XML artifact11832843169 proves 99 tests,
0 failures, 0 errors, 0 skipped; includes 14 Comix tests and 29 reader-domain tests.
Signed optimized test job115435886838 passed R8/ABI/v2 signature, same repository certificate.
Downloaded lint artifact11833992117: 20 errors/408 warnings, zero findings on changed production lines.
This remains diagnostic legacy lint debt, not a clean lint pass.
Public signed 9.8.5 release run38437336280 succeeded; final evidence follows.

## Verified signed 9.8.5 publication

Published 2026-10-13T17:32:16Z: https://github.com/madebycli/Futon-MihonFIx/releases/tag/v9.8.5
Release run38437336280 SUCCESS, source2c708fcf593279243d33650d8d0eae41d00a5bad.
Fresh regressions, optimized R8, host ABI, package io.github.landwarderer.futon, version9.8.5/90805
and v2 signature passed. Certificate SHA256:
12fe17245decb84c98985ddcc08884b874d10a78e4fb747a6ed3e2a42d77b625.
APK asset637756905, 21,354,341 bytes, SHA256:
d4deebca968a4d1696e653fed6d95b7a738e677cea5522963ee632ac41f3e586.
Documentation-only main follow-ups preserve the released app/build/test tree.
Real-device and instrumented runtime retests remain PENDING.
