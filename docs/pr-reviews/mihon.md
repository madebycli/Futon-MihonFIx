# Mihon parity review improvements

Native Activity verification and the Mihon off-screen solver now receive the same Hilt singleton
CloudflareSolveCoordinator. Requests for one host share ownership, failure cooldowns and cancellation.
Last-waiter cancellation cleans up in NonCancellable context, and cancels an owned Activity result.
Host gates are retained to prevent removal/join races creating concurrent owners.

The Mihon helper accepts the ClearanceSolver interface, allowing end-to-end interceptor retry tests
without Android WebViews. One-shot and duplex requests are not automatically replayed.
Diagnostics omit URL credentials/paths/query/fragment values, raw sensitive headers, cookie attribute values
and raw response bodies. Exception diagnostics expose failure types rather than raw exception messages.
Synthetic-secret tests cover the actual diagnostic interceptor as well as its pure formatting helpers.

Historical ABI, exact default interceptor order, Brotli/Zstd, memo/update APIs, child-first bridges,
snapshots and source_<id> preferences remain unchanged. Both release identities and signing stay stable.
Full JVM gates and all 19 optimized ABI symbols are now included in CI.

No Room table, new application dependency, browser-transport subsystem or speculative API-header refill is introduced.
Chapter-only refresh remains a separately scoped repository/tracker change. Live Cloudflare and physical-device tests remain open.
