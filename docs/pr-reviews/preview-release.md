Gemeinsame Testversion der überarbeiteten Draft-PRs #5, #6 und #7. Kein PR wurde nach devel gemergt.

Direkt unter Assets eine APK laden; die automatisch erzeugten Source-code-ZIPs enthalten keine installierbare App.

- Futon-MihonFix-9.9.0-pr5-6-7-preview.1.apk: Standalone (io.github.madebycli.futon.mihonfix), bestehender Repository-Release-Schlüssel.
- Futon-9.9.0-pr5-6-7-preview.1-legacy-update.apk: Legacy-Update (io.github.landwarderer.futon), derselbe bestehende Schlüssel.
- Futon-all-PRs-5-6-7-debug.apk: separate Debug-App (io.github.landwarderer.futon.debug), Android-Debug-Schlüssel. Ersetzt keine signierte Release-App; Datenübernahme über Backup.

Änderungen: unveränderliche Kapitel-Einstellungen und sichere Original-IDs für Reader/History/Downloads/Bookmarks; Markdown-Spans auf Android getestet, nur http(s)-Links klickbar; gemeinsame Cloudflare-Solver-Koordination mit begrenztem Retry und bereinigten Diagnose-Logs.

Verifikation vor Veröffentlichung: vollständige JVM-Tests, Debug-Build, Instrumented-Kompilierung, Android-16-Emulatortests für Beschreibungen und Installation/Updates/Koexistenz; beide optimierten APKs mit lintVitalRelease, 19 ABI-Symbolen, Paket-/Versions-/ARM64- und Signatur-Gate. Exakte Quellen und XML-Testzahlen stehen in BUILD-INFO.txt und TEST-RESULTS.json; Prüfsummen in SHA256SUMS.txt.

Offen: physisches Galaxy S26 Ultra, Cloudflare an echten Seiten und interaktives Reader-Verhalten beim Merge-Umschalten. Vollständiges lintRelease bleibt diagnostisch; vorhandene Lint-Altlasten sind kein bestandenes Gesamt-Lint-Gate. Review der Draft-PRs steht aus.

Vor einem Wechsel zwischen Release und Debug bzw. einem Downgrade ein App-Backup exportieren. Ein Update mit abweichendem Signaturschlüssel bleibt von Android gesperrt.

Generated with Codex.
