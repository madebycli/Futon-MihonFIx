# Futon Handoff: 2026-10-13

Read .ai/CONTEXT.md, STATE.md, graph.yaml, docs/upstream-comix/{PLAN,AUDIT,VALIDATION}.md.
Project futon, canonical repo madebycli/Futon-MihonFIx (ID 1345465394), base devel
6f171b4f9f07ba792d02e1682da779ac739002eb. Historical Futon name is a confirmed rename alias.

Current code: feat/comix-all-chapters-upstream-sync, draft PR #3, Comix commit
f66145ddd8a3b4ad47bd42ee1696a94fea0e7a35. Do not merge devel.
The user separately authorized a signed release; release/v9.8.4 is the existing workflow route,
version 9.8.4/90804, same package/certificate as published 9.8.3. No new key or extension fork.

Scope was simplified by the user: ONLY the external Comix Mihon extension. With trustworthy unique
(volume, number) keys, show all delivered chapters without scanlator chips. Duplicates or uncertain
numbers keep existing groups; no ranking/dedup engine or chapter deletion. Preserve original IDs/URLs.
Reader resume, navigation, downloads and progress use the same projection.

Upstream ports: b2864d65 nested interactive errors; 3e86e87 header ownership adapted to Futon.
Live upstreams and deliberately omitted deltas are in AUDIT.md. Existing snapshot and ABI behavior remains.
Upstream intermediate XML: 56 tests passed. Comix source XML: 69 tests passed, Debug Build successful.
Published 9.8.4 source e754d831497aa40c2d9083dfd40bbb79987203de, run 38361962248 success.
APK asset 636270858, SHA256 0fd0249e2e56a5dcff4995b8c9799f91b671c82f387c557479b5f6f02ec95cca.
R8/ABI/version/signature gates passed; devel is untouched, PR #3 stays unmerged.
Re-fetch actual workflow/release status before relying on artifact availability.
New device validation remains PENDING, regardless of historical successful device tests.
