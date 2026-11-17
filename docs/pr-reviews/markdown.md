# Markdown rendering review

The upstream Markdown parser and source formatting rules remain intact. Only http(s) links stay clickable;
mailto is deliberately retained as non-clickable text. The existing HTML ImageGetter path and expand/collapse UI are retained.
Span filtering now copies its input instead of mutating a cached SpannableString. Invalid negative span ranges are ignored.

Added dependency-free instrumented tests using the existing Android test stack:
HTML scheme allowlist, input immutability, style preservation, inline images, UTF-16 emoji offsets,
invalid/overlapping spans, and unsafe Markdown links. Compilation and actual device/emulator execution are separate gates.
The preview pipeline will execute this class on Android 16 when available; no execution success is claimed before that run.

Full PR review CI runs the complete JVM suite and compiles debug and instrumented sources.
No Room schema, application ID, release signing identity or application dependency changes.
