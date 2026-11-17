# Issues 9–18 code review

| Issue / PR | Reviewed behavior | Evidence and remaining limits |
|---|---|---|
| 9 / 23 | Only chapter section rows opt in to the theme accent. Existing colors are restored when a holder is reused. | The combined test checks real repository output through header mapping. Device contrast and recycling visuals remain open. |
| 10 / 21 | Trimmed empty credits do not create headings in group-only lists. A later repeated credit gets its own heading. Real volumes keep headings, including the unknown-volume group. | JVM tests cover mixed, reversed and repeated groups. |
| 11 / 22 | Complementary groups retain their identities. Merge-on projects one ordered list, and merge-off restores the original groups. | Real repository fixture covers both projections and switching back. Interactive next/previous and toggle behavior remain open. |
| 12 / 20 | German action and explanation use existing Android resource names. | Combined resource build. Visual German review remains open. |
| 13 / 25 | Menu refresh is delayed while an action view owns the sheet lock. View teardown and ActionMode release the appropriate lock. | JVM refresh and teardown tests. UI sequences and theme recreation remain open. |
| 14 / 29 | A unique network-constrained worker compares installed packages to repo metadata. Notifications require user opt-in and system permission, and open extension management. | Unit tests cover version comparison, throttling and repository errors. A persistently failing configured repository keeps the whole check retrying and can delay alerts from working repos. Live permission/onboarding/notification interactions need separate validation. |
| 15 / 26 | Loading, empty, error and visible rows share the source-list path. Refresh requests are conflated; unchanged titles skip DB writes to avoid invalidation loops. | JVM status and refresh tests, Room title test on Android. Physical blank-screen reproduction remains open. |
| 16 / 28 | Source origin is exposed in list/grid text while original names and IDs remain intact. | JVM origin tests cover built-ins, wrapped and persisted Mihon sources. Device layout and accessibility remain open. |
| 17 / 24 | Existing built-in Manga and Novel enable flags survive reconciliation. Recent suggestions filter disabled rows before limiting. The global all-sources mode still controls the effective list. | Room and actual repository tests cover individual disable, rescan, disable-all and DB reopen. Exact intermittent trigger on Galaxy remains unknown. |
| 18 / 27 | The Android 12+ splash and Welcome use the existing Clio icon. About and README describe the rebrand. | Kotlin resources and installation/startup smoke tests. The originally reported screen and light/dark appearance need device confirmation. |

Integration conflicts were limited to the shared English/German string resources and the sources DAO. All relevant changes are preserved. The preview adds a repository-to-header regression assertion and runs the Room and source reconciliation tests together. Full output is attached to the preview build. No data schema, package name or signing-key change.
