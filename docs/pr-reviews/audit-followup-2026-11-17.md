# Review follow-up: Draft PRs 5, 6 and 7

Date: 2026-11-17. Base devel: 0e2e4b1f467338ae5e209344d9c042f0ff23a618.
The user explicitly authorized PR improvements and a combined GitHub prerelease. No authorization to merge into devel. No production branch changes, Room migrations, application dependencies, package identities or signing key changes.

## Chapter groups (#5)

IMPLEMENTIERT: Replaced mutable process-global preference state with immutable MangaDetails/ReaderState snapshots. Screens and progress updates read the actual AppSettings value; restored preferences no longer depend on constructing a singleton at the right time.

Reader projections retain original chapter identities and original stored/remote URLs. Disabling merging can reconstruct a separate branch even if all representatives belong to one group. Completed downloads and deletions invalidate stored snapshots. Downloaded-only rows use a real stored upload, its ID and its local URL; they do not label a newer remote upload as locally available. Bookmark badges aggregate logical chapters while the bookmark record keeps its original ID/page. History fallback is limited to merge mode.

Tracker “new chapter” markers use the recorded last chapter and its original branch before mapping to displayed representatives. Unknown anchors produce no guessed badges. Smart Resume remains conservative where the saved ID is absent from its projected list; it does not guess page correspondence between scanlations.

Kototoro merge keys, representative priorities and section rules are preserved. Existing Futon sorting by (volume,) number is retained and explicitly documented as an adaptation, rather than claiming whole-app identity. Tests cover source ordering, specials, volume separation and fraction numbers. Nonpositive-number chapters still use title identity; the ordering adaptation can place specials at the beginning. A product decision to remove that pre-existing sorting adaptation would be separate work.

Cross-PR integration exercises the real Mihon repository, then MangaDetails and section rendering: 215–222, WebToon before 222.5, Official · WebToon before 223, Saucerer before 223.9, and WebToon can recur at 224. Kototoro's initial unknown-volume header is retained when early chapters have no credits and later chapters have credits.

Quality assessment after corrections: code 7/10, implementation 7/10. The algorithms are small and well sourced; retaining identity, remote and downloaded snapshots adds state complexity to MangaDetails. A future dedicated projection value object could simplify that lifecycle, but replacing the reader model now would add risk without a device test.

## Markdown descriptions (#6)

IMPLEMENTIERT: Invalid negative span offsets are rejected. Filtering copies the Spannable before stripping unsupported colors/links, preserving input objects, styles and inline images. Core Markdown parser remains the upstream port.

Six Android instrumented tests use existing dependencies: HTML scheme filtering, safe copying/style preservation, ImageGetter/ImageSpan behavior, UTF-16 offsets and line breaks, invalid ranges, and unsafe Markdown links. Pure parser tests remain. Expand/collapse production wiring and ImageGetter integration were not changed; full fragment interactions still need manual testing.

mailto links intentionally become plain text, as do javascript, intent, file and content links. Only HTTP(S) links remain clickable. The upstream parser's trailing closing parenthesis for nested javascript syntax is retained; changing the parser would be a separate compatibility decision.

Quality assessment: code 8/10, implementation 8/10 once the Android tests pass. This is a contained adaptation with explicit boundaries. Parser coverage is stronger than full screen interaction coverage.

## Mihon compatibility (#7)

IMPLEMENTIERT: One injected singleton owns an automatic solve for each host across native Activity and Mihon off-screen paths. NonCancellable waiter cleanup prevents abandoned ownership on cancellation; gates are retained so callers cannot join detached locks. Cancellation completes the owned Activity session. Cooldown and one-retry limits remain. One-shot or duplex request bodies are not replayed.

Diagnostics replace raw URLs, response previews, sensitive header values and exception contents with host identities, fingerprints or exception types. Tests capture the actual Mihon helper's diagnostic interceptor with synthetic secrets, exercise its real challenge/retry chain, and cover cancellation, shared ownership and one-shot requests.

No browser-like API header injection was added: source-owned API headers remain authoritative, including with clearance cookies. Kototoro's strategy setting/browser transport is deferred because it introduces another network execution path and UI policy. Core upstream Futon CloudFlareInterceptor/CommonHeadersInterceptor remain authoritative; ownership coordination is added without replacing their behavior. SourceRefreshReporter is deferred because it requires persistent schema work. Chapter-only refresh is deferred because existing refresh/track/history callers expect a complete update; a separate fetch contract is needed before exposing a partial result.

Host ABI (19 symbols), interceptor order, Brotli/Zstd, minSdk 26, child-first bridges, memo/update ABI and chapter snapshots stay gated. Optimized signature validation must use the existing repository certificate and run for both production identities; no disposable key is accepted for the preview gate.

Quality assessment after corrections: code 7/10, implementation 7/10. The bridge remains broad and compatibility-heavy; pure helpers and a testable solver improve reviewability. Synthetic challenge tests do not prove that a current real Cloudflare site accepts the WebView fingerprint.

## Validation and release provenance

Individual full-suite results (tests/failures/errors/skips): #5 247/0/0/1, #6 269/0/0/1, #7 267/0/0/1. All three also built debug and compiled instrumented sources. Combined and Android results are recorded after CI completion. No pass claim is made for a job still running. Full JVM suites include all tests, not only legacy filters. Existing full lintRelease findings remain diagnostic; lintVitalRelease is enforced. Android 16 emulator tests do not represent a physical Samsung Galaxy S26 Ultra.

Status: IMPLEMENTIERT. DRAFT PR OFFEN. REVIEW AUSSTEHEND. GERÄTETEST OFFEN.
