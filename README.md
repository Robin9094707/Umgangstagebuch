# Umgangstagebuch 📖

Eine native Android-App (Kotlin, Jetpack Compose, **Material 3 Expressive**) zum Dokumentieren von Umgängen:
wie die Umgänge laufen, wie es dem Kind vorher und nachher geht, Absagen, Übergaben, Fotos – beweissicher und privat.

## Funktionen

- **Schöne Einrichtung** mit animiertem Splash-Screen: Name der Mama, Kind (Name, Geburtstag, Details), Vater mit echtem Namen
  *und* frei wählbarem Spitznamen samt Emoji (z. B. „Stinktier 🦨“) – in offiziellen Berichten steht immer der echte Name.
- **Eigener Speicherordner** (Pflicht beim ersten Start): Alle Daten und Fotos liegen in einem selbst gewählten Ordner
  (`umgangstagebuch.json`, `fotos/`, `sicherungen/`). Wird die App gelöscht, bleiben die Daten erhalten – nach einer
  Neuinstallation einfach denselben Ordner wählen.
- **Umgänge planen** inkl. Umgangsregelung (jede Woche / alle 2 / alle 4 Wochen), Status: stattgefunden, verkürzt,
  abgesagt (wer, wann, wie, Begründung, Vorlaufzeit – kurzfristige Absagen werden markiert), nicht erschienen.
- **Bericht-Assistent**: führt Schritt für Schritt durch Vorher → Übergaben → Nachher → Erzählungen (wörtlich) → Fotos.
- **Tagebuch** mit Kategorien (Umgangsbericht, Übergabe, Befinden, Kommunikation, Gesundheit, Kita/Schule, Behörden …),
  Stimmungsskala, Beobachtungs-Chips, Fotos (Kamera & Galerie), Suche und Filter.
- **Beweissicherung**: Erfassungszeitpunkt wird automatisch gesetzt, jede Änderung wird mit Zeitstempel und früherer
  Fassung protokolliert, SHA-256-Prüfsummen für Einträge und Fotos (Originalfotos inkl. Aufnahmedaten).
- **Tonaufnahmen**: im Hintergrund aufnehmen (läuft weiter bei ausgeschaltetem Bildschirm, Start/Stopp über die App),
  mit automatisch festgehaltenem Datum, Uhrzeit und Ort. Abspielen mit Wellenform-Ansicht, Umbenennen und Löschen mit
  Bestätigung. Datum, Uhrzeit, Ort und SHA-256-Prüfsumme bleiben als Nachweis fest – nur der Titel ist änderbar.
  *Hinweis in der App:* Heimliche Tonaufnahmen sind vor Gericht oft nicht verwertbar (§ 201 StGB); Android verlangt
  während der Aufnahme eine kleine Benachrichtigung.
- **PDF-Bericht** für Jugendamt, Anwalt oder Gericht – sachlich, mit Statistik, Absagen, Einträgen, Fotos, Aufnahmen-Liste und Prüfsummen.
- **Erinnerungen**: vor jedem Umgang, danach „Wie war der Umgang?“, optional ein wöchentlicher Rückblick.
- **Backup** als ZIP exportieren/importieren (zusammenführen oder ersetzen), tägliche automatische Sicherungen.
- **App-Sperre** per Fingerabdruck / Gesicht / PIN, Hell-/Dunkelmodus, Farben vom Hintergrundbild (Material You).
- Keine Cloud, kein Konto, keine Werbung, keine Internet-Berechtigung.

## Installation auf dem Handy

1. Auf GitHub unter **Releases** die neueste `Umgangstagebuch-x.y.z.apk` herunterladen.
2. Öffnen und „Aus dieser Quelle installieren“ erlauben.
3. Updates einfach genauso installieren – sie ersetzen die alte Version, die Daten bleiben erhalten.

## Entwicklung

- Build: `./gradlew assembleRelease`
- Tests (inkl. Screenshot-Tests mit fiktiven Beispieldaten): `./gradlew testDebugUnitTest` → Bilder in `app/build/screenshots`
- CI: `.github/workflows/android.yml` baut, testet, lintet und veröffentlicht bei jedem Push auf `main` ein Release.

### Signatur

Damit Updates sich über die alte Version installieren lassen, wird immer mit demselben Schlüssel signiert
(`app/signing/umgangstagebuch.keystore`). Für einen privaten Schlüssel können die Secrets
`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` und `SIGNING_KEY_PASSWORD` gesetzt werden
(Achtung: ein Wechsel des Schlüssels erfordert einmalig Deinstallieren & Neuinstallieren – Daten bleiben im Ordner).

Dieses Repository enthält keine persönlichen Daten – alle Angaben werden ausschließlich in der App auf dem Gerät erfasst.
