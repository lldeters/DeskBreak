# DeskBreak im Google Play Store veröffentlichen

Schritt-für-Schritt-Checkliste für die erste Veröffentlichung von Uhr-App und Handy-App mit 3 Tagen Testphase,
Einmalkauf (5 €) und Monatsabo (1 €). Stand der Recherche: **12.11.2026**. Google ändert Menünamen und Regeln
regelmäßig – Stellen mit **Prüfen:** bitte direkt in der Play Console bzw. in deren Hilfe (Fragezeichen-Symbole)
gegenchecken. Die Play-Console-Hilfe (support.google.com) konnte für diese Anleitung nicht direkt abgerufen werden;
Grundlage sind developer.android.com und weitere seriöse Quellen (siehe [Quellen](#quellen)).

## Was liegt wo

| Datei | Inhalt |
|---|---|
| `README.md` (diese Datei) | Ablauf von Konto bis Veröffentlichung, Fehlerbehebung |
| [signing-and-build.md](signing-and-build.md) | Bauen und Signieren der beiden App Bundles (eigene Anleitung) |
| [store-listing.md](store-listing.md) | Texte für den Store-Eintrag (de-DE, en-US) mit geprüften Längen |
| [privacy-policy.md](privacy-policy.md) / [privacy-policy.html](privacy-policy.html) | Datenschutzerklärung (DE/EN); die HTML-Datei ist fertig zum Hochladen |
| [data-safety.md](data-safety.md) | Antworten für „Datensicherheit" und alle anderen Fragebögen unter „App-Inhalte" |
| [assets/](assets/) | App-Symbol, Vorstellungsgrafiken, Screenshots für Handy und Uhr (siehe [Grafiken](#grafiken)) |
| [tools/count_listing.py](tools/count_listing.py) | prüft die Zeichenzahl der Store-Texte |

## Vor der Veröffentlichung ausfüllen

Die Platzhalter bleiben in diesem Repository stehen. Ausgefüllt wird nur die **veröffentlichte Kopie** (bzw. das
Feld in der Play Console):

| Platzhalter | Datei / Stelle | Eintragen |
|---|---|---|
| `[Name]`, `[Anschrift]`, `[E-Mail]` | [privacy-policy.html](privacy-policy.html), Abschnitt 1 und 12, deutscher **und** englischer Teil (ebenso [privacy-policy.md](privacy-policy.md), falls du sie veröffentlichst) | verantwortliche Person bzw. Firma, ladungsfähige Anschrift, Kontakt-E-Mail |
| `[Datum]` | [privacy-policy.html](privacy-policy.html): „Stand" und „Last updated" | Datum der veröffentlichten Fassung |
| `[E-Mail]` | [store-listing.md](store-listing.md), „Weitere Angaben im Store-Eintrag" | öffentliche Kontakt-E-Mail im Store-Eintrag |
| `<benutzer>` | diese Datei, [Abschnitt 7](#7-app-inhalte-ausfüllen) (Adresse bei GitHub Pages) | dein GitHub-Benutzername |
| `[Name]` | [signing-and-build.md](signing-and-build.md), Abschnitt 1 und 2 (`keytool -dname`, Pfad in `keystore.properties`) | nur lokal, wird nicht veröffentlicht |

Das Quell-Repository am besten **privat** halten: Jeder Commit enthält Name und E-Mail-Adresse der Autorin bzw. des
Autors (`git log`). Öffentlich wird nur die ausgefüllte Datenschutzerklärung (eigenes Repository, siehe Abschnitt 7).

## Das Modell in einem Satz

Die App ist im Store **kostenlos**. Ab dem ersten Öffnen (auf Uhr oder Handy) läuft eine **3-tägige Testphase**,
die die App selbst verwaltet (ohne Zahlungsdaten). Danach wählt man zwischen dem **Einmalkauf
`deskbreak_lifetime` (5,00 €)** und dem **Abo `deskbreak_monthly` mit Basis-Abo `monthly` (1,00 € im Monat)**.
Ohne Kauf erinnert die Uhr nicht mehr; Verlauf, Export und Sicherung bleiben nutzbar.

**So wird freigeschaltet:** In der Handy-App auf der Seite „DeskBreak freischalten" (über den Hinweis auf Heute
oder die Lizenz-Karte in den Einstellungen) oder direkt auf der Uhr. „Am Handy freischalten" auf der Uhr öffnet auf
dem gekoppelten Handy genau diese Seite der Handy-App – über den Deep Link `deskbreak://unlock`; ist die Handy-App
dort nicht installiert, stattdessen die Seite von DeskBreak im Play Store. Ein Kauf gilt für Uhr und Handy: über
dasselbe Google-Konto oder den Abgleich der gekoppelten Geräte.

---

## 0. Stand im Code (vor dem ersten Hochladen)

Diese Punkte verhindern sonst das Hochladen oder die Freigabe. Sie betreffen den Code bzw. den Build, nicht die
Play Console – Details in [signing-and-build.md](signing-and-build.md). Abgehakt ist, was im Code erledigt ist;
offen bleibt, was nur du selbst erledigen kannst.

- [x] **Ziel-API-Level.** Seit 31.08.2026 verlangt Play für neue Apps und Updates **API 36 (Android 16)** für
      Handy-Apps und **API 35 (Android 15)** für Wear-OS-Apps
      ([developer.android.com](https://developer.android.com/google/play/requirements/target-sdk)). Im Code:
      Handy (`mobile/build.gradle.kts`) `targetSdk = 36`, Uhr (`app/build.gradle.kts`) `targetSdk = 35`,
      `compileSdk = 36` in allen Modulen. Die Verhaltensänderungen von Android 15/16 sind gegen den Code geprüft
      ([signing-and-build.md, Abschnitt 7](signing-and-build.md#7-geprüft-für-targetsdk-35-uhr-und-36-handy)); auf
      echten Geräten noch ansehen (Zeit-Dialog quer, Predictive Back, Tablet).
- [x] **Eindeutige Versionscodes.** Uhr- und Handy-Bundle haben dieselbe App-ID `dev.stepcron.wear`; ihre
      `versionCode`s müssen über beide Formfaktoren hinweg **verschieden** sein. Einzige Stelle ist
      `deskbreak.versionCode` in `gradle.properties`; daraus berechnen die Build-Skripte **Handy = Basis × 10 + 1**
      und **Uhr = Basis × 10 + 2** (bei Basis 1: Handy 11, Uhr 12). Für jedes Update nur die Basis um 1 erhöhen
      ([signing-and-build.md, Abschnitt 4](signing-and-build.md#4-versionen)).
- [ ] **Gleicher Signaturschlüssel** für Uhr und Handy. Der Build signiert beide Module zentral (Wurzel-
      `build.gradle.kts`) mit demselben Upload-Schlüssel – den Schlüssel musst du einmal selbst anlegen
      ([signing-and-build.md, Abschnitt 1–2](signing-and-build.md#1-upload-schlüssel-erstellen-einmalig)); Play App
      Signing gibt dann beiden Bundles denselben App-Signaturschlüssel. Sonst stellt der Wearable Data Layer
      zwischen den Geräten nichts zu, und die Wear-OS-Richtlinie WO-G7 ist verletzt.
- [ ] **64-Bit:** Seit 15.09.2026 müssen Wear-OS-Apps 64-Bit-Geräte unterstützen. DeskBreak enthält keinen eigenen
      nativen Code, nur zwei kleine Bibliotheken aus AndroidX (siehe signing-and-build.md, Abschnitt 3); im
      fertigen Bundle prüfen, dass es zu jedem 32-Bit-Ordner unter `lib/` auch den 64-Bit-Ordner gibt
      (`arm64-v8a`, `x86_64`). Die Release-APK des Handys enthält alle vier (`arm64-v8a`, `armeabi-v7a`, `x86`,
      `x86_64`); beim Uhr-Bundle genauso nachsehen.
- [x] **Kauf-Oberfläche** auf Handy und Uhr: Preis aus Play, Abrechnungszeitraum, „jederzeit kündbar", automatische
      Verlängerung, Link zur Abo-Verwaltung in Play, Käufe bestätigen (sonst erstattet Play sie nach 3 Tagen),
      Käufe nach Neuinstallation wiederherstellen; während der Testphase steht beim Abo, dass die Abrechnung sofort
      beginnt. Das verlangt die Abo-Richtlinie von Play.
- [x] **Screenshots:** Die Store-Screenshots in [assets/](assets/) entsprechen dem aktuellen Stand (Testphasen- und
      Kaufanzeige sind darauf bewusst nicht zu sehen). Nach Änderungen an der Oberfläche neu erzeugen (siehe
      [Grafiken](#grafiken)).

---

## 1. Entwicklerkonto

1. Unter <https://play.google.com/console/signup> mit einem Google-Konto anmelden, das du dauerhaft für die App
   nutzt (am besten ein eigenes Konto nur dafür).
2. **Kontotyp wählen:**
   - **Privat (persönliches Konto):** für Einzelpersonen. Identitätsnachweis (Ausweis), Adresse,
     Telefonnummer und E-Mail werden verifiziert; zusätzlich kann Google verlangen, dass du Zugriff auf ein echtes
     Android-Gerät nachweist (über die App „Google Play Console").
   - **Organisation:** für Firmen (auch Einzelunternehmen mit Eintragung). Braucht eine **D-U-N-S-Nummer**
     (kostenlos bei Dun & Bradstreet, Ausstellung kann Tage bis Wochen dauern) und eine Organisations-Website.
     **Vorteil:** keine Testpflicht vor der Produktion (siehe unten).
3. **Registrierungsgebühr:** einmalig 25 US-Dollar.
4. **Testpflicht für neue private Konten:** Bei privaten Konten, die nach dem 13.11.2023 angelegt wurden, muss die
   App vor dem Zugang zur Produktion **mindestens 14 Tage ununterbrochen in einem geschlossenen Test mit
   mindestens 12 Testerinnen und Testern** laufen, die sich angemeldet haben (seit Ende 2024 12 statt vorher 20).
   Danach stellst du im Dashboard den Antrag auf Produktionszugriff. Realistisch: 3–4 Wochen von der Kontoeröffnung
   bis zum Start. **Prüfen:** aktuelle Zahlen auf der Dashboard-Seite der Play Console.
5. **Händlerstatus (EU, Digital Services Act):** Weil DeskBreak Käufe anbietet, bist du sehr wahrscheinlich
   „Händler". Dann zeigt Google **Name, Anschrift, Telefonnummer und E-Mail öffentlich** im Store an. Wer seine
   Privatadresse nicht veröffentlichen möchte, braucht eine Geschäftsadresse (oder ein Organisationskonto).
   **Prüfen:** Angaben unter Einstellungen > Entwicklerkonto.
6. **Steuern und Gewerbe** (kurz, keine Steuerberatung): Einnahmen aus App-Verkäufen sind in Deutschland in der
   Regel gewerblich (Gewerbeanmeldung, Einkommensteuer, ggf. Kleinunternehmerregelung). Die Umsatzsteuer gegenüber
   Endkunden in der EU führt Google ab; die Preise in der Play Console sind für EU-Länder **Bruttopreise**. Bitte
   mit einer Steuerberatung klären.

## 2. Zahlungsprofil (Händlerkonto)

Ohne verknüpftes Zahlungsprofil lassen sich keine In-App-Produkte anlegen.

1. Play Console > **Einstellungen > Zahlungsprofil** (bzw. *Monetarisieren > Monetarisierung einrichten*) >
   Zahlungsprofil erstellen oder vorhandenes verknüpfen ([Doku](https://developer.android.com/google/play/billing/getting-ready)).
2. Angaben: Name bzw. Firmenname, Anschrift, Kontakt, **Bankkonto (IBAN)** für die Auszahlungen,
   **Steuerangaben** (für Verkäufe in den USA ein US-Steuerformular, für Privatpersonen in Deutschland meist
   W-8BEN; der Assistent fragt das ab).
3. Google bestätigt das Bankkonto ggf. mit einer kleinen Testüberweisung. Auszahlungen erfolgen monatlich, sobald
   der Mindestbetrag erreicht ist.

## 3. App anlegen

Play Console > **Alle Apps > App erstellen**:

| Feld | Wert |
|---|---|
| App-Name | `DeskBreak – Bewegungspausen` (oder kurz `DeskBreak`, siehe [store-listing.md](store-listing.md)) |
| Standardsprache | **Deutsch (Deutschland) – de-DE** |
| App oder Spiel | App |
| Kostenlos oder kostenpflichtig | **Kostenlos** (gekauft wird in der App; das lässt sich später nicht mehr auf „kostenpflichtig" ändern) |
| Erklärungen | Programmrichtlinien und US-Exportbestimmungen bestätigen |

Danach unter **Store-Präsenz > Haupt-Store-Eintrag > Übersetzungen verwalten** die Sprache **Englisch (USA) –
en-US** hinzufügen.

Die App-ID (Paketname) `dev.stepcron.wear` wird mit dem ersten hochgeladenen Bundle festgelegt und lässt sich nie
mehr ändern.

## 4. Bundles hochladen: Handy und Uhr

Bauen und signieren: siehe [signing-and-build.md](signing-and-build.md). Ergebnis sind zwei App Bundles (`.aab`)
mit derselben App-ID `dev.stepcron.wear`: eines aus `:mobile` (Handy) und eines aus `:app` (Uhr).

1. **Handy-Bundle zuerst:** Testen und veröffentlichen > **Interner Test** > Neuen Release erstellen > Bundle aus
   `:mobile` hochladen. Bei der ersten Veröffentlichung **Play App Signing** aktivieren (Google verwaltet den
   App-Signaturschlüssel; du signierst nur mit dem Upload-Schlüssel).
2. **Formfaktor Wear OS hinzufügen:** Testen und veröffentlichen > **Erweiterte Einstellungen > Formfaktoren >
   Formfaktor hinzufügen > Wear OS**. Damit entstehen eigene **Wear-OS-Tracks** (interner Test, geschlossener Test,
   Produktion), getrennt von den Handy-Tracks
   ([developer.android.com](https://developer.android.com/training/wearables/packaging)).
3. **Uhr-Bundle hochladen:** Auf den Release-Seiten oben den Formfaktor bzw. Track **„Nur Wear OS"** auswählen und
   dort das Bundle aus `:app` hochladen. Nicht ins Handy-Track hochladen. **Prüfen:** genaue Bezeichnung des
   Auswahlfelds.
4. Beide Bundles müssen mit **demselben Schlüssel** signiert sein und **verschiedene Versionscodes** haben
   (Abschnitt 0). Die Uhr-App ist als eigenständig markiert (`com.google.android.wearable.standalone = true`) und
   benötigt das Handy nicht.
5. Unter Formfaktoren der **Wear-OS-Prüfung zustimmen** (Opt-in). Erst dann wird die Uhr-App für Wear-OS-Geräte
   freigegeben; den Status (Ausstehend / Genehmigt / Nicht genehmigt) zeigt die Console dort an.

## 5. Produkte anlegen

Voraussetzungen: Zahlungsprofil verknüpft (Abschnitt 2) und mindestens ein Bundle mit der Google Play Billing
Library in einem Track hochgeladen (Abschnitt 4) – erst dann schaltet die Console die Produktseiten frei.

### 5.1 Einmalkauf `deskbreak_lifetime`

**Monetarisieren > Produkte > In-App-Produkte** (heißt teils *Einmalkaufprodukte*) > Produkt erstellen:

| Feld | Wert |
|---|---|
| Produkt-ID | `deskbreak_lifetime` (exakt so, nicht änderbar) |
| Name | DE: „DeskBreak dauerhaft freischalten" · EN: „Unlock DeskBreak permanently" |
| Beschreibung | DE: „Einmalzahlung, kein Abo – schaltet die Erinnerungen auf Uhr und Handy frei." · EN: „One-time payment, no subscription – unlocks reminders on your watch and phone." |
| Kaufoption | eine Option vom Typ **Kaufen** (nicht „Leihen"); **Prüfen:** Die neuere Console verlangt pro Produkt eine Kaufoption mit eigener ID, z. B. `buy` |
| Preis | **5,00 EUR** als Standardpreis; die Console rechnet die anderen Länder um – Rundungen prüfen |
| Status | **Aktiv** |

Der Einmalkauf ist „nicht verbrauchbar": Die App bestätigt ihn nur (acknowledge) und verbraucht ihn nie. Das legt
der Code fest, nicht die Console.

Keine Besitz- oder „für immer"-Versprechen (Play-Richtlinie zu irreführenden Angaben, § 5 UWG): Der Kauf schaltet
die App frei, er verkauft sie nicht. Deshalb „dauerhaft freischalten" bzw. „Einmalzahlung, kein Abo" – dieselbe
Wortwahl wie in der App und im Store-Eintrag.

### 5.2 Abo `deskbreak_monthly` mit Basis-Abo `monthly`

**Monetarisieren > Produkte > Abos** > Abo erstellen:

| Feld | Wert |
|---|---|
| Produkt-ID | `deskbreak_monthly` |
| Name | DE: „DeskBreak Monatsabo" · EN: „DeskBreak monthly" |
| Vorteile (optional) | „Erinnerungen auf Uhr und Handy", „Jederzeit kündbar" |
| **Basis-Abo** | ID **`monthly`**, Typ **Automatische Verlängerung**, Abrechnungszeitraum **1 Monat** |
| Preis | **1,00 EUR**, andere Länder umrechnen lassen und prüfen |
| Kulanzzeitraum / Kontosperre | Standard lassen (die App vertraut einer Abo-Bestätigung ohnehin bis zu 35 Tage) |
| Erneutes Abonnieren | erlaubt |
| Angebote | **keine** (siehe unten) |
| Status | Basis-Abo **aktivieren** |

**Warum kein kostenloser Probezeitraum in Play?** Die 3 Tage verwaltet die App selbst: Sie gelten für beide
Kaufarten (auch für den Einmalkauf, für den Play gar keinen Probezeitraum kennt), beginnen ohne Zahlungsdaten und
enden ohne Abo-Falle. Ein Probe-Angebot in Play würde dagegen eine Zahlungsmethode verlangen, nur für das Abo gelten
und sich mit der App-Testphase addieren. Ein Play-Angebot (z. B. Einführungspreis) bleibt später optional möglich;
der Code würde dann das Basis-Abo bzw. das Angebot auswählen müssen – vorher prüfen.

Folge: Wer während der Testphase abonniert, zahlt sofort. Das steht in der App beim gewählten Abo direkt unter dem
Kaufknopf („Die Abrechnung beginnt heute – mit dem Abo endet die Testphase.").

Nach dem Anlegen dauert es teils einige Stunden, bis die Produkte in der App erscheinen.

## 6. Testen mit Lizenztestern und Testkäufen

1. **Lizenztester:** Play Console (Ebene Entwicklerkonto, nicht App) > **Einstellungen > Lizenztests** > die
   Google-Konten (Gmail) der Testpersonen eintragen. Lizenztester zahlen mit **Testzahlungsmitteln** („Testkarte,
   immer genehmigt", „… immer abgelehnt", „Langsame Testkarte, wird nach einigen Minuten genehmigt/abgelehnt") und
   sehen im Kaufdialog einen Testhinweis
   ([developer.android.com](https://developer.android.com/google/play/billing/test)).
2. **Interner Test:** Testen und veröffentlichen > Interner Test > Tester hinzufügen (E-Mail-Liste) > Link zum
   Teilnehmen teilen. Auf **Handy und Uhr** denselben Track bzw. den passenden Wear-OS-Track nutzen und die App
   **aus Play installieren** (auf der Uhr über den Play Store der Uhr oder vom Handy aus „Auf Uhr installieren").
   Interne Tests und Entwürfe haben Ausgabenlimits pro Tag.
3. **Testfälle:**
   - [ ] Erststart auf der Uhr: Testphase „noch 3 Tage"; danach auf dem Handy dieselbe Restlaufzeit.
   - [ ] Auf der Uhr „Am Handy freischalten" → das Handy zeigt „DeskBreak freischalten" der Handy-App; Zurück führt
         auf Heute. Auch wenn die Handy-App schon offen ist (keine zweite Kopie der App). Ohne Handy-App öffnet sich
         die Play-Store-Seite. Von Hand: `adb shell am start -a android.intent.action.VIEW -d deskbreak://unlock`.
   - [ ] Testphase, Abo gewählt: Unter dem Kaufknopf steht, dass die Abrechnung heute beginnt.
   - [ ] Ende der Testphase: Datum auf dem Gerät um 4 Tage vorstellen → Uhr erinnert nicht mehr, Verlauf/Export
         gehen weiter. Datum zurückstellen.
   - [ ] Einmalkauf mit „Testkarte, immer genehmigt" → freigeschaltet, auch auf dem anderen Gerät.
   - [ ] Abo mit Testkarte → freigeschaltet. Test-Abos verlängern sich **alle 5 Minuten, höchstens 6 Mal**, dann
         enden sie (ca. 30–35 Minuten) – das ist so gewollt.
   - [ ] Abo in Play kündigen → läuft bis Periodenende, danach Ende der Freischaltung (bei Testkonten nach Minuten).
   - [ ] „Langsame Testkarte" → Kauf „ausstehend", Freischaltung erst nach Abschluss.
   - [ ] App neu installieren → Kauf wird wiederhergestellt.
   - [ ] Erstattung in der Console (Bestellungen verwalten > Erstatten und Berechtigung entziehen) → Freischaltung
         endet nach der nächsten Abfrage. So lässt sich der Einmalkauf auch erneut testen.
   - [ ] Nicht bestätigte Käufe werden bei Lizenztestern nach **3 Minuten** automatisch erstattet – passiert das,
         bestätigt die App den Kauf nicht (Fehler im Code).
4. **Play Billing Lab** (App von Google) hilft beim Testen weiterer Fälle (Land wechseln, Abo-Zustände
   beschleunigen).

## 7. App-Inhalte ausfüllen

Unter **Richtlinien und Programme > App-Inhalte**. Alle Antworten mit Begründung stehen in
[data-safety.md](data-safety.md). Kurzfassung:

| Fragebogen | Antwort |
|---|---|
| Datenschutzerklärung | URL der veröffentlichten `privacy-policy.html` (Platzhalter vorher füllen) |
| App-Zugriff | eingeschränkt nach der Testphase; Anleitung ohne Zugangsdaten (Text in data-safety.md) |
| Anzeigen | keine Werbung |
| Einstufung (IARC) | Dienstprogramme/Produktivität; alles „Nein" außer „digitale Käufe: Ja" |
| Zielgruppe | 18+ (optional 16–17), spricht keine Kinder an |
| Datensicherheit | **keine Daten erhoben oder geteilt** (Begründung in data-safety.md) |
| Gesundheits-Apps | Kategorie „Aktivität und Fitness", kein Medizinprodukt |
| Finanzfunktionen | keine |
| Nachrichten-App / Behörden-App | nein / nein |
| Werbe-ID | nein |
| Vordergrunddienste | Typ „Health" für die Schritterkennung, mit Beschreibung und **Video-Link** |
| Exakte Alarme | keine Erklärung nötig: die Uhr nutzt `SCHEDULE_EXACT_ALARM` (Nutzer erlaubt „Wecker und Erinnerungen", sonst ungenaue Alarme), nicht das eingeschränkte `USE_EXACT_ALARM` |

Die Datenschutzerklärung veröffentlichen, z. B. mit **GitHub Pages**: ein eigenes, öffentliches Repository (etwa
`deskbreak-privacy`) anlegen, die Platzhalter in `privacy-policy.html` füllen (siehe
[Vor der Veröffentlichung ausfüllen](#vor-der-veröffentlichung-ausfüllen)), die Datei dort als `index.html`
hochladen, unter *Settings > Pages* den Branch als Quelle wählen und die Adresse
`https://<benutzer>.github.io/deskbreak-privacy/` eintragen. Die Seite braucht keine weiteren Dateien. (Pages direkt
aus diesem Repository würde den ganzen Ordner `docs/` veröffentlichen und setzt bei privaten Repositories ein
kostenpflichtiges GitHub-Konto voraus.)

## 8. Store-Eintrag

**Wachstum > Store-Präsenz > Haupt-Store-Eintrag** (und dieselben Felder in der Übersetzung en-US):

1. **Texte** aus [store-listing.md](store-listing.md): App-Name, Kurzbeschreibung, vollständige Beschreibung.
2. **App-Symbol:** `assets/icon-512.png` (512 × 512, volle Fläche, Play rundet die Ecken selbst ab).
3. **Vorstellungsgrafik:** `assets/feature-graphic-de.png` bzw. `-en.png` (1024 × 500).
4. **Handy-Screenshots:** `assets/phone-de-*.png` bzw. `phone-en-*.png` (je 7, 1080 × 1920; Play erlaubt 2–8).
5. **Wear-OS-Screenshots:** im Abschnitt für Wear OS (erscheint nach dem Hinzufügen des Formfaktors)
   `assets/wear-de-*.png` bzw. `wear-en-*.png` (je 4, 454 × 454, quadratisch). Pflicht: mindestens einer, der die
   App auf Wear OS zeigt (WO-G5). Empfohlen zusätzlich ein Screenshot der **Kachel** – den gibt es hier nicht
   automatisch; im Emulator aufnehmen (Android Studio, „Take screenshot" > „Play Store Compatible").
6. **Kategorie und Kontakt** unter **Store-Einstellungen**: Kategorie „Gesundheit und Fitness", E-Mail (öffentlich),
   optional Website.
7. Tablet-Screenshots sind optional; ohne sie wird die Handy-App auf Tablets trotzdem angeboten.

## 9. Testen, dann veröffentlichen

1. **Interner Test** (Abschnitt 6) bis alles läuft, inkl. Pre-Launch-Bericht der Console ansehen.
2. **Geschlossener Test** für Handy- **und** Wear-OS-Track anlegen, Länder wählen, Tester-Liste (E-Mail-Liste oder
   Google Group) hinzufügen, Release zur Prüfung senden. Bei neuen privaten Konten: **≥ 12 Tester, ≥ 14 Tage
   ununterbrochen** (Abschnitt 1). Tester sollen die App wirklich nutzen und Feedback geben; Google fragt danach.
3. **Produktionszugriff beantragen** (Dashboard): Fragen zum Test beantworten (wie Tester gefunden, welches
   Feedback, was geändert). Prüfung dauert meist einige Tage.
4. **Produktion:** Release für Handy-Track und Wear-OS-Track erstellen (oder aus dem geschlossenen Test
   übernehmen), Versionshinweise aus [store-listing.md](store-listing.md), Länder (mindestens Deutschland, Österreich,
   Schweiz; gern weltweit) und optional **gestaffelte Einführung** (z. B. 20 %). Zur Prüfung senden; die erste
   Prüfung kann bei neuen Konten länger dauern.
5. Nach der Freigabe: Bewertungen beantworten, Android vitals (Abstürze, ANRs) beobachten, Updates immer mit
   höherem `versionCode` (Uhr und Handy getrennt) hochladen.

## 10. Servicegebühr von Google

Für Käufe von Nutzerinnen und Nutzern im **EWR, in Großbritannien und den USA** gilt seit **30.06.2026**: **10 %
Servicegebühr** auf die ersten 1 Mio. US-Dollar Jahreserlös (Abos generell 10 %) plus **5 % Abrechnungsgebühr**,
wenn – wie bei DeskBreak – Google Play Billing genutzt wird, also **15 % insgesamt**. In anderen Ländern gilt bis zur
dortigen Umstellung das bisherige Modell (15 % auf die ersten 1 Mio. US-Dollar und auf Abos). **Prüfen:** aktuelle
Sätze und ob eine Anmeldung (z. B. Kontogruppe) nötig ist, in der Play-Console-Hilfe „Servicegebühren".

Rechenbeispiel Deutschland (19 % USt., führt Google ab; Gebühr auf den Nettobetrag):

| | Einmalkauf | Abo pro Monat |
|---|---:|---:|
| Preis im Store | 5,00 € | 1,00 € |
| abzüglich Umsatzsteuer | 4,20 € | 0,84 € |
| abzüglich 15 % | **3,57 €** | **0,71 €** |

## 11. Fehlerbehebung

**Produkte oder Preise erscheinen nicht in der App / „Produkt nicht gefunden"**
- Produkt bzw. Basis-Abo in der Console **aktiv**? Produkt-IDs exakt `deskbreak_lifetime`, `deskbreak_monthly`,
  Basis-Abo `monthly`?
- App **aus dem Play-Testtrack installiert** (nicht per Android Studio)? Nur Lizenztester dürfen auch
  selbst installierte Builds mit passender App-ID nutzen.
- Testperson im Track **angemeldet** (Link zum Teilnehmen geöffnet) und bei Käufen als **Lizenztester** eingetragen?
- Auf dem Gerät ist **dasselbe Google-Konto** im Play Store aktiv, mit dem getestet wird (bei mehreren Konten zählt
  das Konto, das die App installiert hat).
- Nach dem Anlegen der Produkte einige Stunden warten; Play-Store-Cache auf dem Gerät leeren.
- App im Land der Testperson verfügbar?

**Kauf gilt nur auf einem Gerät**
- Auf Uhr und Handy muss im Play Store **dasselbe Google-Konto** angemeldet sein (oder die Geräte melden sich den
  Kauf über den Abgleich – dazu beide Apps einmal öffnen, Bluetooth an, Geräte in der Nähe).
- Uhr- und Handy-App müssen aus Play stammen bzw. **gleich signiert** sein, sonst stellt der Data Layer nichts zu.

**Testkauf hat echtes Geld gekostet**
- Das Konto war beim Kauf nicht als Lizenztester eingetragen. In der Console erstatten.

**Test-Abo endet nach einer halben Stunde**
- Normal: Test-Abos verlängern sich alle 5 Minuten, höchstens 6 Mal.

**Uhr-App im Play Store der Uhr nicht zu finden**
- Formfaktor Wear OS hinzugefügt und Wear-OS-Prüfung zugestimmt? Release im **Wear-OS-Track** (nicht im
  Handy-Track)? Tester auch für den Wear-OS-Test angemeldet? Uhr mit Wear OS 3 oder neuer?

**Hochladen abgelehnt**
- „Zielt auf API-Level …" → `targetSdk` anheben (Abschnitt 0).
- „Versionscode bereits verwendet" → höheren, über beide Formfaktoren eindeutigen `versionCode` nehmen.
- „Falsch signiert" → mit dem registrierten Upload-Schlüssel signieren ([signing-and-build.md](signing-and-build.md)).

**Wear-OS-Prüfung abgelehnt**
- Die E-Mail von Google nennt die Gründe. Häufig: kein passender Wear-OS-Screenshot, Funktion nicht wie
  beschrieben, Darstellung auf runden Displays fehlerhaft. Beheben, erneut zustimmen und veröffentlichen.

---

## Grafiken

Alle Bilder liegen in [assets/](assets/), sind 24-Bit-PNG ohne Transparenz (wie von Play verlangt) und zeigen
Beispieldaten (Dienstag, 10:22 Uhr, gut fünf Wochen Verlauf).

| Datei | Größe | Verwendung |
|---|---|---|
| `icon-512.png` | 512 × 512 | App-Symbol (aus den Ebenen des Launcher-Symbols) |
| `feature-graphic-de.png`, `feature-graphic-en.png` | 1024 × 500 | Vorstellungsgrafik je Sprache |
| `phone-de-1-today.png` … `phone-de-7-settings-data.png` | 1080 × 1920 | Handy-Screenshots Deutsch: Heute, Verlauf (Kalender, Wochentage, Tageszeit), Einstellungen (Erinnerungen, Wochenplan, Schritterkennung und Daten) |
| `phone-en-1-today.png` … `phone-en-7-settings-data.png` | 1080 × 1920 | dieselben auf Englisch |
| `wear-de-1-home.png` … `wear-de-4-exercise.png` | 454 × 454 | Wear-OS-Screenshots Deutsch: Startseite, Auswertung (7 Tage, 5 Wochen), 1-Minuten-Übung |
| `wear-en-1-home.png` … `wear-en-4-exercise.png` | 454 × 454 | dieselben auf Englisch |

Vollständige Liste:

```text
assets/icon-512.png
assets/feature-graphic-de.png            assets/feature-graphic-en.png
assets/phone-de-1-today.png              assets/phone-en-1-today.png
assets/phone-de-2-history.png            assets/phone-en-2-history.png
assets/phone-de-3-history-weekdays.png   assets/phone-en-3-history-weekdays.png
assets/phone-de-4-history-hours.png      assets/phone-en-4-history-hours.png
assets/phone-de-5-settings.png           assets/phone-en-5-settings.png
assets/phone-de-6-settings-schedule.png  assets/phone-en-6-settings-schedule.png
assets/phone-de-7-settings-data.png      assets/phone-en-7-settings-data.png
assets/wear-de-1-home.png                assets/wear-en-1-home.png
assets/wear-de-2-stats-week.png          assets/wear-en-2-stats-week.png
assets/wear-de-3-stats-month.png         assets/wear-en-3-stats-month.png
assets/wear-de-4-exercise.png            assets/wear-en-4-exercise.png
```

**Neu erzeugen** (z. B. nach Änderungen an der Oberfläche).
Die Bilder rendert Robolectric ohne Gerät aus den echten Bildschirmen:

```sh
export ANDROID_HOME=$HOME/android-sdk
./gradlew :mobile:testDebugUnitTest -Pscreenshots --tests '*StoreAssetsTest*'   # Symbol, Vorstellungsgrafik, Handy
./gradlew :app:testDebugUnitTest -Pscreenshots --tests '*StoreScreenshotTest*'  # Uhr
cp mobile/build/screenshots/store/*.png app/build/screenshots/store/*.png docs/play-store/assets/
```

Quellcode: `mobile/src/test/java/dev/stepcron/mobile/screenshots/StoreAssetsTest.kt` und
`app/src/test/java/dev/stepcron/wear/screenshots/StoreScreenshotTest.kt`. Die Kachel lässt sich so nicht rendern
(sie wird vom System der Uhr gezeichnet) – dafür den Emulator nutzen.

Anforderungen von Play (zum Nachprüfen): App-Symbol 512 × 512, 32-Bit-PNG, max. 1024 KB, volle quadratische Fläche
ohne eigene Rundung/Schatten ([Icon-Spezifikation](https://developer.android.com/distribute/google-play/resources/icon-design-specifications)).
Vorstellungsgrafik 1024 × 500, JPEG oder 24-Bit-PNG ohne Alpha. Handy-Screenshots: JPEG oder 24-Bit-PNG, Seiten
320–3840 px, die lange Seite höchstens doppelt so lang wie die kurze; für Empfehlungen mindestens 4 Screenshots mit
≥ 1080 px. Wear OS: Seitenverhältnis 1:1, mindestens 384 × 384, nur die App-Oberfläche, kein Uhrenrahmen, keine
Transparenz oder Maske (WO-G5).

---

## Quellen

- Ziel-API-Level: <https://developer.android.com/google/play/requirements/target-sdk>
- Wear-OS-Apps verpacken und verteilen (Formfaktor, Tracks, Prüfung): <https://developer.android.com/training/wearables/packaging>
- Wear-OS-Qualitätsrichtlinien (WO-G2, WO-G5, WO-G7, WO-G8, 64-Bit): <https://developer.android.com/docs/quality-guidelines/wear-app-quality>
- Google Play Billing vorbereiten: <https://developer.android.com/google/play/billing/getting-ready>
- Google Play Billing testen (Lizenztester, Testkarten, Verlängerungszeiten): <https://developer.android.com/google/play/billing/test>
- Datenschutzformular, Datentypen: <https://developer.android.com/privacy-and-security/declare-data-use>
- Wearable Data Layer (Verschlüsselung, Zustellung nur an gleich signierte App): <https://developer.android.com/training/wearables/data/overview>
- Vordergrunddienst-Typen (`health`): <https://developer.android.com/develop/background-work/services/fgs/service-types>
- Exakte Alarme (`SCHEDULE_EXACT_ALARM`, „Wecker und Erinnerungen"): <https://developer.android.com/develop/background-work/services/alarms/schedule>
- Google-Play-Symbol: <https://developer.android.com/distribute/google-play/resources/icon-design-specifications>
- Android-Entwicklerverifizierung (für Apps aus Play automatisch registriert): <https://developer.android.com/developer-verification>
- Testpflicht neuer privater Konten (12 Tester, 14 Tage) und neue Gebührenstruktur ab 30.06.2026: Play-Console-Hilfe
  bzw. Android-Developers-Blog, hier nur über Sekundärquellen bestätigt – in der Console prüfen.
