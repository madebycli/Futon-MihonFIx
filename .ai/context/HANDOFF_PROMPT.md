# Futon Handoff: actual AppFuton parent sync, 2026-10-13

Read .ai/CONTEXT.md and its validated central route first, then STATE.md, graph.yaml and
docs/upstream-comix/APPFUTON-SYNC.md. Project futon, repo madebycli/Futon-MihonFIx
(ID 1345465394), main/default branch devel.

Correction: the earlier audit only checked Kototoro/Keiyoushi compatibility references.
Actual fork parent AppFuton/Futon had six missing commits through 5d2206a6926adf53be0867ad784948ed16663f44.
All original commits are preserved by two-parent merge 5dc3735972f75f5c946b67eef28c47b5a48ea0c6.
PR #4 merged into devel at 2c708fcf593279243d33650d8d0eae41d00a5bad. GitHub behind_by=0.
Upstream v9.8.2 progress race protection, optional smart resume, adjacent prefetch and parser b3c0000245
are included. The user explicitly authorized actual upstream integration and signed publication.

Mihon fixes are still required: none of the six commits modifies the host ABI/runtime layers.
Preserve minSdk26, interceptors, classloader, modern/legacy ABI and durable snapshots.
Only app changes for external Comix: trustworthy unique (volume, number) keys share one complete
list without group chips. Duplicates/uncertain numbers keep groups and original IDs/URLs.
Smart resume uses this same projection, including cross-scanlator successors. No extension fork/new key.

99 focused tests passed, zero errors/failures/skips, artifact 11832843169 in run38436109226.
Debug run38436109198 succeeded, including compileDebugAndroidTestKotlin.
Signed 9.8.5/90805 release run38437336280 from release/v9.8.5 succeeded and is published.
APK asset637756905, SHA256 d4deebca968a4d1696e653fed6d95b7a738e677cea5522963ee632ac41f3e586.
Released source2c708fcf593279243d33650d8d0eae41d00a5bad; same certificate as9.8.4.
Re-fetch live release status before claiming publication; current final proof is in APPFUTON-SYNC.md.
New device and instrumented runtime execution remains PENDING. Historical device success does not
validate this new tree. README stays English, GitHub-only downloads, original style.
