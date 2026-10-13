# AppFuton upstream sync and 9.8.5

## Ziel
Merge the actual fork parent AppFuton/Futon history and publish a new signed downstream APK.
Base c7e9c2389a3f120f07301e1c4fec22a98f3724e3, upstream devel 5d2206a6926adf53be0867ad784948ed16663f44.
The earlier audit inspected compatibility references, not this fork parent. Its no-missing-upstream
claim did not cover AppFuton; this audit corrects that mistake.

## Anforderungen und Architektur
Android/Kotlin, existing Reader/ViewModel/Room/Mihon runtime. Preserve package/signature and minSdk 26.
Merge with two parents so all six original SHAs become ancestors and GitHub behind count becomes zero.
Keep upstream smart resume opt-in. Preserve downstream Comix grouping, reader history and extension ABI.
No extension fork, new signing secret or schema migration. Existing pure resolver/shared projection,
O(n) chapter processing, no new dependency or extra background network loop.

## Delta-Matrix
| Commit | Change | Integration |
| --- | --- | --- |
| af2688f02b32e5f340272e6ba22a9c5f2bf2c21d | progress race protection | retain transaction/snapshot comparison plus Comix projection |
| a57a103324afbfa5d1b00bae481b1547d0a09bdb | configurable smart resume | merge settings, resolver and tests; use complete Comix chapter list |
| e28754d6462edf4912b260f1bc28c7610109293e | parser 38cfb00b0a | original history retained; superseded by final upstream b3c0000245 pin |
| 311cfbf7746350e4a9ecc5d74109012729abce75 | adjacent chapter prefetch | merge loader/prefetch logic and regression tests |
| 555af9a059a0728791b5664b098b043f35b499ab | upstream 9.8.2 / parser b3c0000245 | keep upstream changelog; downstream APK uses 9.8.5/90805 |
| 5d2206a6926adf53be0867ad784948ed16663f44 | setup-android v4 | use v4 with platform-tools; keep hardened signing/release gates |

## Mihon-Fix weiterhin erforderlich
None of the six commits changes mihon/ or eu/kanade/tachiyomi/ host implementations.
Upstream still targets minSdk 23; our modern extension runtime requires 26. The six commits do not
supply our modern Source/HttpSource/model ABI, memo snapshots, classloader and interceptor fixes.
Therefore our compatibility layer is retained and its existing regression/optimized ABI gates run again.

## Konflikte / Dateistruktur
Workflow setup: v4 plus downstream parameters/gates. Versions catalog: parser b3c0000245 while retaining
host OkHttp 5.5.0 / Okio 3.18.1. Changelog: retain both histories. ReaderViewModel: upstream startup
resolver + downstream combined Comix selection; remove duplicate startup load during conflict resolution.
Affected production/tests match upstream diff plus the explicit integration points above.

## Umsetzungsschritte und Tests
1. Live parent/compare/release audit, clean feature branch, real two-parent history merge.
2. Resolve conflicts; add smart-resume crossing scanlator-groups regression.
3. Run reader-domain and existing Mihon/Comix tests, compile instrumentation sources, debug APK.
4. Merge to devel after gates pass; publish signed 9.8.5 from verified source; check certificate and APK hash.
5. Verify AppFuton head is an ancestor of devel / GitHub ahead-by for upstream is zero; update both contexts.

## Offene Fragen / UNKLAR
New unit/build CI pending at initial write. No attached Android device/emulator: runtime instrumentation
and device tests PENDING. Full lint has pre-existing debt; new findings must be checked separately.
