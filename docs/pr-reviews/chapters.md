# Chapter review improvements

Merge mode remains ON by default. The Kototoro merge-key, representative priority and section algorithms remain intact.
The pre-existing numeric/volume ordering is retained as an explicit Futon adaptation for interleaved groups.

- Removed process-global ChapterMergeSetting; each MangaDetails now carries an immutable merge preference.
- Settings reads have no domain-model side effects. Details/reader and progress updates receive explicit snapshots.
- Reader observes the first preference emission as well, preserving changes made during startup.
- Downloaded-only mode lists real stored uploads with their original IDs/URLs, even when a newer remote upload is the normal representative.
  The normal list continues to follow Kototoro's latest-upload priority and does not falsely label a remote file as stored.
- Bookmark badges aggregate logical chapter identity; stored bookmark IDs and target pages are not rewritten.
- New chapter labels/counts project the tracker's original branch and last-chapter anchor into representatives.
  An unknown anchor produces no speculative new markers.
- Cross-branch history fallback is restricted to merge mode. Smart resume remains conservative if old projection identity is uncertain.
- Added regression tests for immutable settings snapshots, stored older uploads, bookmark identity and tracker projection.
- Full PR review CI now executes every JVM test plus debug/instrumented-source compilation; the legacy focused lane includes new feature packages and all 19 release ABI symbols.

Status: implementation added; current CI and device results must be checked before claiming validation.
No application ID, signing key, Room schema or application dependency changes.

Reader projections retain original and stored chapter identity snapshots, including when every representative belongs to a single group. Restore and cross-branch-history regressions are covered by additional tests.
