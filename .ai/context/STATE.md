# Current Futon Compatibility and Comix State

Last refreshed: 2026-10-13

## Active work

- Repository madebycli/Futon-MihonFIx, ID 1345465394; old Futon name is a rename alias.
- Main/default branch devel now includes all feature commits through merge 78befa347ef37abed2d1d76ec0cd531e063f2a7e.
  Previous base: 6f171b4f9f07ba792d02e1682da779ac739002eb.
- Main-branch synchronization is explicitly authorized by the user on 2026-10-13 (evening).
  PR #3 is MERGED into devel at 78befa347ef37abed2d1d76ec0cd531e063f2a7e.
  PR #1 is CLOSED and was not merged through GitHub.
- Published v9.8.3 on 2026-09-02, run 33611369866 success. Older release-prep statements below are historical.
- Signed 9.8.4/90804 is published from release/v9.8.4. The user has now separately authorized
  synchronization into devel. Never replace existing published releases or generate new keys.
- Published 9.8.3 certificate SHA256: 12fe17245decb84c98985ddcc08884b874d10a78e4fb747a6ed3e2a42d77b625
  (verified in run logs). AppValidator/CI.md carry an older differing fingerprint; not changed in this scope.

## Current code and evidence

- Targeted Kototoro ports: b2864d65 (nested interactive IO failures), 3e86e87 (extension header ownership,
  adapted by removing Futon's own API-header refill; CommonHeaders already injects only native parser headers).
- Upstream intermediate source fa7907fe85ca1b07602e96c3d7c0f9666c64b09b, run 38360422794:
  downloaded focused XML reports prove 56 tests, 0 failures, 0 errors. Subsequent run superseded its lint/build.
- Comix source f66145ddd8a3b4ad47bd42ee1696a94fea0e7a35; 13 new regression tests: run 38361172927 XML reports prove 69 tests, 0 failures/errors;
  Debug Build run 38361172910 success. Signed optimized release 9.8.4 PUBLISHED: source e754d831497aa40c2d9083dfd40bbb79987203de,
  run 38361962248 SUCCESS, APK asset 636270858, SHA256
  0fd0249e2e56a5dcff4995b8c9799f91b671c82f387c557479b5f6f02ec95cca.
  R8/ABI/version/signature gates passed, same release certificate as 9.8.3.
- User simplified scope: external Comix ONLY, reliable unique (volume, number) keys become one complete
  chapter list with no group chips. Duplicates/unknown numbers keep groups. No host dedup/ranking engine,
  no Extension fork/preferences changes, no chapter deletion, no new persisted state or DB migration.
- Reader/resume/progress/download mapping use the same projection; original IDs/URLs/scanlators remain intact.
- Force refresh bypasses details cache; extension itself handles dedup preference changes through list memo.
- Real-device retest of these new changes PENDING. Historical device success below applies only to its recorded tree.
- Detailed plan/audit: docs/upstream-comix/; final CI/publication evidence is recorded in VALIDATION.md.

## Historical compatibility and release preparation

## Final real-device validation

The user reported on 2026-09-01 that the final repository-key build works successfully: `alles geht`.

This closes the former runtime node `POST_78D_REPOSITORY_KEY_DEVICE_VALIDATION`.

Authoritative device-tested application tree:

- Source/test head: `78d128189277167cd2f0c84979c9f94139b9ff05`
- Tree: `2848e13c2b26566137a4a252a6f5c418fee8d012`
- Last source fix: `809a8900f9f662b516b61eb7443cbf6c78021e6a`
- PR synthetic merge built by Actions: `6f690e43de883bc71897e5fa9c70cfc9c49d88eb`
- Synthetic merge tree: `2848e13c2b26566137a4a252a6f5c418fee8d012`, identical to the tested source tree

Final tested signed workflow:

- Workflow: `Mihon Fix Signed Test Build`
- Run: `33536918663`, run number `295`, success
- Focused Mihon regression suite: 49/49 success
- Release lint: success
- Optimized R8 release: success
- Optimized Mihon runtime ABI gate: success
- APK signature verification: success
- Artifact: `Futon-Mihon-Fix-Signed-Release`
- Artifact id: `9812861083`
- Artifact digest: `sha256:e174ceb153e80c28ee70b9a883a174a12a074c76513c2d066c5b3c65eac9e367`
- APK: `Futon-9.8.1-mihon-fix-test-signed-release.apk`
- APK SHA-256: `4f0bdca5bc1bf29f37663485275dacedc25adf36d20ee58fb10cfe4cf1b6b745`
- Signing: `repository-release-key`

The user-reported real-device success has higher evidentiary priority than CI and closes the current Mihon runtime validation requirement.

## Release-ready preparation

Release target: `Futon 9.8.3`, `versionCode 90803`, future tag `v9.8.3`.

Reason for `9.8.3`: the successfully tested repository-key APK already uses Android `versionCode 90803`. The repository release workflow derives `versionCode` from `MAJOR.MINOR.PATCH`, so `v9.8.2` would produce `90802` and would be a downgrade relative to the tested build. `9.8.3` preserves the tested Android version identity.

Release preparation commit: `9b50dbd18a6e75cd9726511ec09158711fa785dd`.

Release-prep changes:

- `gradle.properties` defines authoritative `versionName=9.8.3` and `versionCode=90803`; `app/build.gradle` consumes them as project properties.
- `CHANGELOG.md` contains a 9.8.3 entry.
- `docs/releases/9.8.3.md` contains user-facing GitHub release notes.
- `.github/workflows/release.yml` now gates publication on tag/version consistency, repository signing secrets, focused Mihon regression tests, hard release lint, optimized R8 build, optimized APK ABI inspection, APK version identity, APK signature verification, release-key signing, named APK output and SHA-256 checksum.
- The workflow publishes `Futon-9.8.3.apk`, `Futon-9.8.3.apk.sha256` and `BUILD-INFO.txt` only after all release gates pass.
- No `v9.8.3` tag has been created and no public GitHub Release has been published in this preparation round.

Current release-prep validation run:

- `Mihon Fix Signed Test Build` run `33545883702`, run number `297`, source `9b50dbd18a6e75cd9726511ec09158711fa785dd`.
- Status must be fetched live before finalizing release readiness. Do not assume this run is green from this context entry alone.

## Implemented Mihon/Keiyoushi compatibility

- Required Mihon default application-interceptor order: `UncaughtExceptionInterceptor`, `UserAgentInterceptor`, `CloudflareInterceptor`.
- Mihon client derived from the full Futon host `OkHttpClient`, while incompatible compression interceptors are removed from the modern default client.
- Host-visible Brotli and Zstd runtime support for dynamically loaded extensions.
- Modern suspend Source/HttpSource API plus compatibility with older custom Rx `fetch*` implementations.
- `SManga.memo`, `SChapter.memo`, `SMangaUpdate`, `Source.getMangaUpdate(...)`, current popular/latest/search/filter/page APIs and request/source context.
- Host `minSdk = 26` to avoid dynamically loaded serializer/default-method ABI failures.
- Kototoro-style Tachiyomi APK classloader ownership policy with host-owned ABI namespaces and child-first `$-CC` / `$DefaultImpls` bridges.
- Source browser-origin preservation, shared Mihon preference namespace, protobuf extension metadata handling, extension repository failure isolation and fallback version parsing.
- Historical Chromium/WebView Cloudflare solving with changed-clearance detection and original-request retry.
- Shared process-local chapter snapshots across repository instances.
- Durable host-owned manga/chapter snapshot persistence across app process restarts.
- `AwaitingMihonMangaRepository` handles restored content until the asynchronous Mihon extension scan completes.
- Restore fidelity fix preserves independent modern and legacy fields such as `genres`/`genre`, `number`/`chapter_number`, and `scanlators`/`scanlator`.

## Current upstream references

Kototoro:

- Repository: `Kototoro-app/Kototoro`
- Branch: `devel`
- Last live verified head: `b2c20e84298bfcc806567d784c8cb6607b1c919f`
- It is 32 commits ahead of the previously recorded `19cbb079...` audit point.
- The exact `TachiyomiApkClassLoaderPolicy.kt` and `KotoNetworkHelper.kt` reference paths did not appear in that compare delta.
- Kototoro's richer Cloudflare/captcha orchestration remains an unverified parity delta, not a confirmed Futon root cause.

Keiyoushi extensions-lib:

- Repository: `keiyoushi/extensions-lib`
- Branch: `main`
- Last live verified head: `42255ee5fa96d9425697b7c143587483207308d1`
- This head merged the 1.6 line and introduced explicit ABI tracking.
- Futon's current Source/HttpSource host contract was compared against this 1.6 API and no missing current Source/HttpSource ABI entry was found. Futon intentionally retains extra legacy/fork compatibility.

## Historical resolved root causes

Do not rediscover these as new bugs without current device evidence:

1. Missing required default-client `UncaughtExceptionInterceptor` contract.
2. Missing host-visible `okhttp3.brotli.BrotliInterceptor` runtime.
3. `GeneratedSerializer.typeParametersSerializers()` `AbstractMethodError` from the old minSdk/interface-desugaring boundary.
4. Obsolete details/chapter repository path.
5. `SManga.getMemo` `NoSuchMethodError`.
6. `Source.getMangaUpdate` `NoSuchMethodError`.
7. Mihon 1.6 MangaDex `0 manga` continuity family.
8. Repository-instance-local chapter snapshot loss.
9. Loss of independent legacy model fields during persisted snapshot restore.

## Next release actions

Once release-prep CI is green:

1. Mark PR #1 Ready for Review.
2. Do not merge until the user explicitly requests the merge.
3. After the user explicitly requests release publication, merge to `devel`, verify the merged commit live, then create `v9.8.3` on the merged release commit.
4. The tag-triggered release workflow will perform all production gates and publish the GitHub Release only if they pass.

## Main-branch integration / README update (2026-10-13 evening)

User authorized updating the main branch; repository main/default branch is devel.
All functional changes already exist in published 9.8.4. Integration preserves the six feature commits,
followed by documentation-only README/context changes. No additional Kototoro/extensions-lib host
commits are available. extensions-source 44eeaaad3f64ef9c7418afe5b2303e79d09fcf2f changes external
Comix 1.6.48 assets/pager/captcha behavior only; update the external extension separately.
README stays English and keeps its layout; GitHub Releases replaces unsupported store badges/buttons.
No application/source/test code, APK version or signing identity changed in this documentation follow-up.

Verified PR #3 merged successfully at 78befa347ef37abed2d1d76ec0cd531e063f2a7e. The complete app/build/test tree
matches v9.8.4 byte-for-byte; documentation-only follow-up requires no new APK or repeat build.
All old fix/release/feature branch heads are ancestors of the updated main branch.
