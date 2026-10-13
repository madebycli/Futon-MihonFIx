# Futon: Upstream und Comix Alle

## Ziel
Gezielte aktuelle Mihon-Fixes und eine kombinierte Comix-Kapitelliste.

## Anforderungen
Comix-only anhand Source-ID/Extension-Paket; bei eindeutigen Nummern keine Gruppen-Tabs; Original-IDs/URLs behalten;
Gruppen weiterhin erreichbar; keine Netzwerkabfrage beim Chipwechsel; keine Migration oder Extension-Fork.
Tests, Debug-APK und optimierter signierter Build. Der Benutzer hat anschließend einen signed Release autorisiert.

## Architektur
A: lokale Projektion einer einzigen ungefilterten Kapitelgruppe bei eindeutigen Nummern, gemeinsam für Details und Reader.
Passt zu RecyclerView/ViewBinding/Flow; O(n log n), keine neuen Abhängigkeiten und keine DB-Migration.
B: separate Repository-Liste würde Reader, Speicherung und Downloads duplizieren und den Originaldatensatz
verändern. Nicht gewählt. Keine neue Ranking-/Dedup-Logik: unklare oder doppelte Nummern behalten bestehende Gruppen.
Keine dynamischen Extension-Klassen im neuen State.

## Dateistruktur
- docs/upstream-comix/{PLAN,AUDIT,VALIDATION}.md
- mihon/MihonExceptionUnwrap.kt und compat/MihonNetworkHelper.kt
- details/domain/ComixChapterProjection.kt, DetailsViewModel, ChaptersPagesViewModel
- details/data/MangaDetails.kt, reader/data/ModelMapping.kt, ReaderViewModel/ReaderState
- passende Regressionstests und bestehende Actions-Gates

## Umsetzungsschritte
1. Identität und Kontext live validieren.
2. ABI und relevante Upstream-Deltas vergleichen; A separat committen und fokussierte CI abwarten.
3. Comix-Projektion und Details/Reader integrieren, Regressionen ergänzen.
4. CI, APK, R8, Signatur und ABI prüfen, PR erstellen, Kontext aktualisieren.
5. Nach erfolgreichen Gates signed Release auslösen; Release-Branch erlaubt ohne Merge nach devel.

## Offene Fragen / UNKLAR
- Gerätetest dieser neuen Änderungen PENDING; kein Android-Gerät verfügbar.
- Lokale Gradle-/SDK-Downloads blockiert, GitHub Actions als Build-Evidenz verwenden.
- Snapshot-Limits können Ranking-Metadata alter Kapitel verdrängen; niemals Werte erfinden.
