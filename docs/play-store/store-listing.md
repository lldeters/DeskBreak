# Store-Eintrag: Texte für Google Play

Zum Kopieren in die Play Console unter **Wachstum > Store-Präsenz > Haupt-Store-Eintrag** (Standardsprache
Deutsch, de-DE) und als Übersetzung **Englisch (USA), en-US**. Grafiken: siehe [README.md](README.md#grafiken).

Die Längen sind mit [`tools/count_listing.py`](tools/count_listing.py) geprüft (zählt Zeichen wie Play, also
Umlaute, „ß" und Gedankenstriche jeweils als ein Zeichen). Nach jeder Textänderung erneut ausführen:

```sh
python3 docs/play-store/tools/count_listing.py
```

| Feld | Grenze | de-DE | en-US |
|---|---:|---:|---:|
| App-Name | 30 | 27 | 27 |
| Kurzbeschreibung | 80 | 77 | 80 |
| Vollständige Beschreibung | 4000 | 2695 | 2467 |
| Versionshinweise 1.0 | 500 | 338 | 327 |

Hinweise zu den Texten:

- **Transparenz beim Preis:** Testphase (3 Tage, ohne Zahlungsdaten, endet von selbst) und beide Kaufoptionen
  stehen in der Beschreibung, ebenso was ohne Kauf weiter geht. Die Preise sind die deutschen Preise inkl. MwSt.;
  im Store zeigt Google den Preis des jeweiligen Landes. Ändern sich die Preise in der Play Console, hier
  mitändern.
- **Wear OS:** „Wear OS", „Kachel" und „Komplikation" werden genannt (Wear-OS-Qualitätsrichtlinie WO-G2: Hauptfunktionen
  nennen, Kachel/Komplikation erwähnen, nicht „Android Wear" schreiben).
- **Keine Werbesprache** im Namen (kein „gratis", „Nr. 1", keine Emojis) – das verbietet die Metadaten-Richtlinie von
  Play. In der Beschreibung ist die sachliche Nennung der Testphase erlaubt.
- **Kein Heilversprechen:** DeskBreak ist ein Erinnerungs-Timer, kein Medizinprodukt; der Hinweis am Ende der
  Beschreibung bleibt drin.
- Die Kaufmöglichkeit auf der Uhr bzw. auf dem Handy wird noch fertiggestellt. Falls ein Kauf nur auf einem der
  beiden Geräte möglich sein sollte, den Absatz „Preise" entsprechend anpassen.

---

## Deutsch (de-DE)

### App-Name (max. 30 Zeichen)

<!-- de:title -->
```text
DeskBreak – Bewegungspausen
```

Alternative, falls der Name kürzer sein soll: `DeskBreak` (9 Zeichen).

### Kurzbeschreibung (max. 80 Zeichen)

<!-- de:short -->
```text
Erinnert dich auf der Wear-OS-Uhr ans Aufstehen. Gehen zählt automatisch mit.
```

### Vollständige Beschreibung (max. 4000 Zeichen)

<!-- de:full -->
```text
DeskBreak erinnert dich auf deiner Wear-OS-Uhr daran, beim Arbeiten regelmäßig aufzustehen und dich zu bewegen – per Vibration und nur zu den Zeiten, die du festlegst. Gehst du ohnehin ein paar Schritte, erkennt die Uhr das automatisch und zählt es als Pause.

AUF DER UHR (Wear OS, auch ohne Handy nutzbar)
• Erinnerung im festen Intervall, z. B. alle 45 Minuten (5 bis 120 Minuten)
• Wochenplan: für jeden Wochentag eigene Zeiten – oder frei
• Snooze, „Jetzt bewegt“ und Pause für ein Meeting, den Rest des Tages oder den Urlaub
• Automatische Schritterkennung: Gehen zählt als Bewegungspause und setzt den Timer zurück
• Ruhemodi: keine Erinnerung bei „Nicht stören“, Schlafenszeit oder Kinomodus – und nicht, wenn die Uhr abgelegt ist
• Tagesziel für deine Bewegungspausen
• Kachel und Komplikation: Countdown bis zur nächsten Pause direkt auf dem Zifferblatt, „Jetzt bewegt“ mit einem Tipp
• Bewegungsideen in der Erinnerung und eine 1-Minuten-Übung mit Timer
• Auswertung: Regelmäßigkeit, Serie, heutiger Tag, die letzten 7 Tage, Monatsübersicht und deine beste und schwächste Tageszeit
• Vibration einstellbar: aus, sanft, mittel oder stark

AUF DEM HANDY (optionale Begleit-App)
• Heute auf einen Blick: Pausen, Tagesziel und nächste Erinnerung
• Verlauf: Kalender der letzten 5 Wochen, Wochentage, Tageszeiten und jeder einzelne Tag
• Alle Einstellungen der Uhr bequem vom Handy aus ändern, auch den Wochenplan
• Export als CSV-Tabelle sowie Sicherung und Wiederherstellung

PREISE
• 3 Tage kostenlos testen – mit allen Funktionen, ohne Abo und ohne Zahlungsdaten. Die Testphase beginnt beim ersten Öffnen auf der Uhr oder dem Handy und endet von selbst.
• Danach entscheidest du: einmalig 5,00 € für immer oder 1,00 € pro Monat (jederzeit über Google Play kündbar).
• Ohne Kauf erinnert die Uhr nach der Testphase nicht mehr. Verlauf, Export und Sicherung bleiben nutzbar – es sind deine Daten.
• Ein Kauf gilt für Uhr und Handy, solange auf beiden dasselbe Google-Konto angemeldet ist.
Die Preise gelten für Deutschland inklusive Mehrwertsteuer; in anderen Ländern zeigt Google Play den Preis in der Landeswährung an.

PRIVATSPHÄRE
• Keine Werbung, kein Tracking, keine Analyse-Tools, kein Benutzerkonto.
• Deine Daten bleiben auf deinen Geräten. Uhr und Handy gleichen sich direkt über die Google Play-Dienste ab, verschlüsselt und ohne Server des Entwicklers.
• Käufe wickelt Google Play ab; der Entwickler erhält keine Zahlungsdaten.

VORAUSSETZUNGEN
Eine Uhr mit Wear OS 3 oder neuer. Die Handy-App braucht Android 8.0 oder neuer und eine gekoppelte Wear-OS-Uhr mit DeskBreak.

DeskBreak ist ein Erinnerungs-Timer und kein Medizinprodukt; die App ersetzt keine ärztliche Beratung.
```

### Versionshinweise 1.0 (max. 500 Zeichen)

<!-- de:notes -->
```text
Die erste Version von DeskBreak:
• Bewegungserinnerungen auf der Wear-OS-Uhr mit Wochenplan, Snooze und Pausen
• Automatische Schritterkennung, Ruhemodi und Tagesziel
• Kachel und Komplikation fürs Zifferblatt
• Handy-App mit Verlauf, Einstellungen, CSV-Export und Sicherung
3 Tage kostenlos testen, danach einmalig 5 € oder 1 € im Monat.
```

---

## Englisch (en-US)

### App name (max. 30 characters)

<!-- en:title -->
```text
DeskBreak – Movement Breaks
```

Alternative: `DeskBreak` (9 characters).

### Short description (max. 80 characters)

<!-- en:short -->
```text
Wear OS reminders to stand up and move. Walking counts as a break automatically.
```

### Full description (max. 4000 characters)

<!-- en:full -->
```text
DeskBreak reminds you on your Wear OS watch to get up from your desk and move regularly – with a vibration, and only at the times you choose. If you walk a few steps anyway, the watch notices and counts it as a break.

ON THE WATCH (Wear OS, works without a phone)
• Reminders at a fixed interval, e.g. every 45 minutes (5 to 120 minutes)
• Weekly schedule: your own times for each weekday – or a day off
• Snooze, "Just moved" and pause for a meeting, the rest of the day or your vacation
• Automatic step detection: walking counts as a movement break and resets the timer
• Quiet modes: no reminders during Do not disturb, bedtime or theater mode – or when the watch is off your wrist
• Daily goal for your movement breaks
• Tile and complication: countdown to your next break right on the watch face, "Just moved" with one tap
• Exercise ideas in every reminder and a 1-minute exercise with a timer
• Stats: regularity, streak, today, the last 7 days, a month overview and your best and weakest time of day
• Adjustable vibration: off, light, medium or strong

ON THE PHONE (optional companion app)
• Today at a glance: breaks, daily goal and next reminder
• History: calendar of the last 5 weeks, weekdays, times of day and every single day
• Change all watch settings from your phone, including the weekly schedule
• CSV export plus backup and restore

PRICING
• Try it free for 3 days – all features, no subscription and no payment details. The trial starts when you first open DeskBreak on your watch or phone and ends on its own.
• Then you choose: a one-time purchase of €5.00 or €1.00 per month (cancel anytime in Google Play).
• Without a purchase, the watch stops reminding you after the trial. History, export and backup keep working – it's your data.
• One purchase covers watch and phone as long as both use the same Google account.
Prices shown are for Germany including VAT; Google Play shows the price for your country in your local currency.

PRIVACY
• No ads, no tracking, no analytics, no account.
• Your data stays on your devices. Watch and phone sync directly via Google Play services, encrypted and without any developer servers.
• Purchases are handled by Google Play; the developer never receives your payment details.

REQUIREMENTS
A watch with Wear OS 3 or later. The phone app needs Android 8.0 or later and a paired Wear OS watch with DeskBreak.

DeskBreak is a reminder timer, not a medical device, and does not replace medical advice.
```

### Release notes 1.0 (max. 500 characters)

<!-- en:notes -->
```text
The first version of DeskBreak:
• Movement reminders on your Wear OS watch with weekly schedule, snooze and pauses
• Automatic step detection, quiet modes and a daily goal
• Tile and complication for your watch face
• Phone app with history, settings, CSV export and backup
Try it free for 3 days, then €5 once or €1 per month.
```

---

## Weitere Angaben im Store-Eintrag

| Feld | Empfehlung |
|---|---|
| App-Kategorie | **Gesundheit und Fitness** (Alternative: Produktivität) |
| Tags | bis zu 5 aus der Liste der Play Console, passend zu Bewegung/Fitness, Gesundheit und Produktivität |
| E-Mail-Adresse (Pflicht, öffentlich) | [E-Mail] |
| Website (optional) | z. B. die Seite, auf der die Datenschutzerklärung liegt |
| Telefonnummer (optional) | nur angeben, wenn sie öffentlich sein darf (als „Händler" nach EU-Recht wird sie ohnehin angezeigt, siehe README) |
| Datenschutzerklärung | URL der veröffentlichten `privacy-policy.html` |
