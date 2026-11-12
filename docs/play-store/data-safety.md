# Datensicherheit und weitere „App-Inhalte" – empfohlene Antworten

In der Play Console unter **Richtlinien und Programme > App-Inhalte** (engl. *Policy and programs > App content*)
müssen vor der ersten Veröffentlichung mehrere Fragebögen ausgefüllt werden. Hier stehen die empfohlenen
Antworten für DeskBreak mit Begründung. Stand: Code-Stand vom 12.11.2026 (Berechtigungen aus den
zusammengeführten Release-Manifesten von `:app` und `:mobile` geprüft).

> **Wichtig:** Ändert sich etwas an Berechtigungen, Bibliotheken (z. B. ein Absturz-Reporting oder Analyse-SDK),
> an der Synchronisierung oder an den Käufen, müssen diese Antworten und die
> [Datenschutzerklärung](privacy-policy.md) neu geprüft werden. Falsche Angaben im Datenschutzformular können zur
> Sperrung von Updates führen. Die Bezeichnungen der Menüs und Fragen in der Play Console können leicht abweichen;
> die Hilfe-Links in der Console (Fragezeichen neben jeder Frage) sind maßgeblich.

---

## 1. Datensicherheit (Data safety)

### 1.1 Was Google unter „erheben" und „teilen" versteht

Kurzfassung der Definitionen aus der Play-Console-Hilfe „Datensicherheit" (*Provide information for Google Play's
Data safety section*) und dem Entwickler-Leitfaden
[developer.android.com/privacy-and-security/declare-data-use](https://developer.android.com/privacy-and-security/declare-data-use):

- **Erheben (collect)** heißt: Die App überträgt Daten **vom Gerät weg** (an den Entwickler, an Server, an Dritte).
- Daten, die **nur auf dem Gerät** verarbeitet werden und es nie verlassen, müssen **nicht** angegeben werden.
- **Ende-zu-Ende-verschlüsselte** Übertragungen, die nur Absender und Empfänger lesen können (nicht der Entwickler,
  keine Zwischenstelle), gelten nicht als Erhebung.
- **Teilen (share)** heißt: Weitergabe an Dritte. Ausgenommen sind u. a. Weitergaben, die die Nutzerin bzw. der
  Nutzer selbst auslöst und erwartet, Dienstleister im Auftrag des Entwicklers und gesetzliche Pflichten.
- Auch Daten, die eingebundene **Bibliotheken/SDKs** übertragen, zählen zur App.

### 1.2 Datenflüsse von DeskBreak und Bewertung

| Datenfluss | Verlässt das Gerät? | Bewertung |
|---|---|---|
| Einstellungen, Verlauf, Status, Lizenzstatus (Datenbank und DataStore auf der Uhr, lokaler App-Speicher auf dem Handy) | nein | nur auf dem Gerät – nicht angeben |
| Schrittsensor, Off-Body-Sensor, „Nicht stören"/Schlafenszeit/Kinomodus | nein (nur ausgewertet, keine Rohdaten gespeichert) | nur auf dem Gerät – nicht angeben |
| Abgleich Uhr ↔ Handy (Einstellungen, Verlauf bis 400 Tage, Lizenzstatus, Sicherungen) über die Wearable Data Layer API | ja, aber nur zum **eigenen anderen Gerät** derselben Person; per verschlüsseltem Bluetooth oder **Ende-zu-Ende-verschlüsselt** über Google-Server ([Doku](https://developer.android.com/training/wearables/data/overview): „All data transferred through Google Cloud is end-to-end encrypted"); nur dieselbe App mit derselben Signatur kann sie lesen | keine Erhebung (Ende-zu-Ende-Ausnahme, Entwickler kann nichts lesen), keine Weitergabe |
| Käufe über Google Play Billing | Kauf und Zahlung laufen in Google Play; die App fragt über die Play-Store-App auf dem Gerät nur ab, ob ein gültiger Kauf besteht, und schickt nichts an eigene Server. Play Store bzw. Play-Dienste tauschen dabei Kauf- und technische Daten mit Google aus | die App selbst erhebt nichts; Verarbeitung durch Google als eigenständig Verantwortlicher, Zahlungsdaten bleiben bei Google (siehe 1.5) |
| Android-Sicherung (`allowBackup="true"`) | ja, in die Sicherung des **eigenen Google-Kontos**, von Android verwaltet (bei gesetzter Displaysperre auf neueren Android-Versionen Ende-zu-Ende-verschlüsselt) | Systemfunktion, der Entwickler hat keinen Zugriff – üblicherweise nicht angegeben (siehe 1.4) |
| CSV-Export und Sicherungsdatei | nur wenn die Person es auslöst, an einen selbst gewählten Ort (Android-Dateidialog) | vom Nutzer ausgelöste Übertragung – nicht angeben |
| Absturzberichte (Android vitals) | sendet Android an Google, wenn die Person der Diagnose-Weitergabe zugestimmt hat | Funktion des Betriebssystems, kein SDK in der App – nicht angeben |
| `INTERNET`/`ACCESS_NETWORK_STATE` | kommen aus Google Play Billing bzw. den Play-Diensten; DeskBreak hat keinen eigenen Netzwerkcode und keine Analyse-, Werbe- oder Crash-SDKs | – |

Geprüft im Code: keine Werbe-ID (`AD_ID` fehlt im zusammengeführten Manifest), keine Standort-, Kontakt-,
Kamera-, Mikrofon- oder Speicherberechtigung, keine Analyse- oder Absturz-Bibliothek in `gradle/libs.versions.toml`.

### 1.3 Empfohlene Antworten im Formular

1. **„Erhebt oder teilt deine App einen der erforderlichen Nutzerdatentypen?"** → **Nein**.
   Damit entfallen die Folgefragen (Verschlüsselung, Löschung, Datentypen).
2. Vorschau prüfen: Im Store steht dann „Keine Daten werden mit Drittanbietern geteilt" und „Keine Daten erhoben".
3. Speichern und zur Überprüfung einreichen.

### 1.4 Wenn Google nachfragt oder du lieber vorsichtig angibst

Google wertet zum Teil automatisch aus, ob Apps Daten vom Gerät senden. Sollte eine Warnung „nicht angegebene
Datenübertragung" kommen oder möchtest du bewusst vorsichtig sein, ist diese Angabe ebenfalls korrekt (sie
übertreibt eher):

| Frage | Antwort |
|---|---|
| Erhebt oder teilt die App Daten? | Ja |
| Werden alle erhobenen Daten bei der Übertragung verschlüsselt? | Ja |
| Können Nutzer die Löschung ihrer Daten beantragen? | Ja (Deinstallieren löscht alles; Kontakt per E-Mail) |
| Datentyp **Gesundheit und Fitness > Fitness-Informationen** | erhoben: ja · geteilt: nein · vorübergehend verarbeitet: nein · optional (Auto-Schritte lassen sich abschalten) · Zweck: App-Funktionen |
| Datentyp **Finanzinformationen > Kaufverlauf** | erhoben: ja · geteilt: nein · vorübergehend verarbeitet: nein · erforderlich · Zweck: App-Funktionen |

Empfehlung bleibt aber **„Nein"** (1.3), weil die Daten das Gerät nur Ende-zu-Ende-verschlüsselt zum eigenen
zweiten Gerät verlassen und der Entwickler sie nie erhält.

### 1.5 Google Play Billing: warum es trotzdem bei „Keine Daten erhoben" bleibt

Die Billing-Bibliothek (`com.android.billingclient`, Version 9.1.0) ist ein SDK in der App, und Daten, die SDKs
übertragen, zählen grundsätzlich zur App (1.1). Kauf und Kaufabfrage laufen aber über die **Play-Store-App bzw. die
Google Play-Dienste** auf demselben Gerät: Die Bibliothek ruft deren Schnittstelle auf, und erst diese tauschen
Kaufdaten (Produkt, Kaufzeitpunkt, Status des Abos) und technische Daten mit Google aus – beim Kauf, den die Person
selbst auslöst, und bei der Abfrage bestehender Käufe beim Öffnen. Das geschieht im Rahmen von Google Play, mit
**Google als eigenständig Verantwortlichem** nach dessen
Datenschutzerklärung (so auch in der [Datenschutzerklärung](privacy-policy.md), Abschnitt 5). Es ist Verarbeitung
durch Google, nicht durch DeskBreak: Die App schickt nichts an den Entwickler oder an Dritte, und der Entwickler
erhält keine Zahlungs- oder Nutzungsdaten – nur die Bestellübersicht in der Play Console.

**Prüfen:** Vor dem Einreichen in der Play-Console-Hilfe „Datensicherheit" und in Googles Angaben zu den eigenen
SDKs (SDK-Index bzw. Datenangaben zur Play Billing Library) nachsehen, ob Google für die Billing-Bibliothek
Datentypen nennt, die Apps selbst angeben sollen. Falls ja, diese übernehmen (vermutlich „Finanzinformationen >
Kaufverlauf", wie in der vorsichtigen Variante 1.4). Nennt Google nichts, ist „Nein" (1.3) die ehrliche Antwort.

---

## 2. Weitere Fragebögen unter „App-Inhalte"

### 2.1 Datenschutzerklärung

URL der veröffentlichten [privacy-policy.html](privacy-policy.html) eintragen (z. B. über GitHub Pages oder eine
eigene Website). Pflicht für alle Apps; für DeskBreak zusätzlich wegen der Berechtigung „Körperliche Aktivität".
Die Seite muss öffentlich erreichbar sein, darf kein PDF sein und muss den App-Namen bzw. Entwickler nennen.

### 2.2 App-Zugriff

DeskBreak hat keine Anmeldung, aber nach der Testphase sind die Erinnerungen nur mit Kauf aktiv. Empfehlung:
**„Alle oder einige Funktionen sind eingeschränkt"** wählen und eine Anleitung ohne Zugangsdaten hinterlegen
(Benutzername/Passwort leer lassen bzw. „nicht erforderlich"):

> **DE:** Keine Anmeldung erforderlich. Nach dem ersten Öffnen auf Uhr oder Handy sind 3 Tage lang alle Funktionen
> freigeschaltet (Testphase, ohne Zahlungsdaten). Danach erinnert die Uhr nur nach einem Kauf über Google Play
> (Einmalkauf „deskbreak_lifetime" oder Monatsabo „deskbreak_monthly"); Verlauf, Export und Sicherung bleiben ohne
> Kauf nutzbar. Die Uhr-App funktioniert eigenständig; die Handy-App zeigt Daten erst an, wenn eine gekoppelte
> Wear-OS-Uhr mit DeskBreak verbunden ist.
>
> **EN:** No login required. After the first launch on the watch or phone, all features are unlocked for 3 days
> (trial, no payment details). After that, the watch only sends reminders after a purchase through Google Play
> (one-time product "deskbreak_lifetime" or monthly subscription "deskbreak_monthly"); history, export and backup
> keep working without a purchase. The watch app works standalone; the phone app shows data once a paired Wear OS
> watch with DeskBreak is connected.

Optional: für das Prüfteam einen **Aktionscode** (Promo-Code) für `deskbreak_lifetime` erzeugen
(Bereich *Promotions* bzw. „Werbeaktionen" unter Monetarisieren; **Prüfen:** genauer Menüname) und in der
Anleitung angeben. Die Wear-OS-Qualitätsrichtlinie
WO-G8 verlangt, dass das Prüfteam bezahlte Funktionen testen kann.

### 2.3 Anzeigen

**„Nein, meine App enthält keine Werbung."**

### 2.4 Einstufung des Inhalts (IARC-Fragebogen)

- E-Mail-Adresse für die Einstufung angeben.
- Kategorie: **„Dienstprogramme, Produktivität, Kommunikation oder Sonstiges"** (*Utility, Productivity,
  Communication, or Other*).
- Gewalt, Sexualität, Sprache, Drogen, Glücksspiel, Horror: **alle Nein**.
- Können Nutzer miteinander interagieren oder Inhalte austauschen? **Nein**.
- Teilt die App den Standort mit anderen? **Nein**.
- Ermöglicht die App den Kauf digitaler Waren? **Ja** (Einmalkauf und Abo).
- Uneingeschränkter Internetzugang (Browser)? **Nein**.

Erwartetes Ergebnis: USK ab 0 / PEGI 3 / ESRB „Everyone", mit dem Hinweis „In-App-Käufe".

### 2.5 Zielgruppe und Inhalte

- Altersgruppen: **18 und älter** (optional zusätzlich 16–17). Keine Altersgruppe unter 13 wählen – sonst gilt die
  Familienrichtlinie mit zusätzlichen Pflichten.
- „Könnte die App Kinder ansprechen?" → **Nein** (Büro-/Arbeitskontext, keine kindgerechte Gestaltung).

### 2.6 Nachrichten-App

**Nein.**

### 2.7 Behörden-App

**Nein.**

### 2.8 Finanzfunktionen

**„Meine App bietet keine Finanzfunktionen."** (In-App-Käufe zählen nicht als Finanzfunktion.)

### 2.9 Gesundheits-Apps (Health apps)

Diese Erklärung müssen alle Apps abgeben. Für DeskBreak: Kategorie **„Aktivität und Fitness"** (Bewegungspausen,
Schritterkennung). Keine medizinischen Funktionen, kein Health Connect, keine Körperwerte. Falls gefragt: Die App
ist **kein Medizinprodukt**.

### 2.10 Werbe-ID

**„Nein"** – die App nutzt keine Werbe-ID (im zusammengeführten Manifest steht keine
`com.google.android.gms.permission.AD_ID`).

### 2.11 Vordergrunddienste (`FOREGROUND_SERVICE_HEALTH`)

Pflicht, weil die Uhr-App (`targetSdk` ≥ 34) einen Vordergrunddienst vom Typ `health` startet
(`StepTrackerService`). Laut Android-Doku ist `health` für „lang laufende Anwendungsfälle von Fitness-Apps" gedacht
und setzt u. a. die Laufzeitberechtigung `ACTIVITY_RECOGNITION` voraus – die die App anfragt.

- Typ: **Gesundheit (Health)**; Aufgabe aus der Liste möglichst nah an „Aktivitäts-/Fitness-Tracking" wählen
  (genaue Bezeichnung in der Console prüfen).
- Beschreibung (DE/EN zum Einfügen):

  > Die Wear-OS-App erkennt während der vom Nutzer festgelegten aktiven Zeiten (Wochenplan), ob er einige Schritte
  > geht, und zählt das als Bewegungspause. Dafür wertet ein Vordergrunddienst vom Typ „health" den Schrittsensor
  > (Schrittdetektor/-zähler) aus. Er läuft nur, wenn „Auto-Schritte" eingeschaltet und die Berechtigung „Körperliche
  > Aktivität" erteilt ist, nur innerhalb der aktiven Zeit, und ist als laufende Benachrichtigung
  > „Schritterkennung aktiv" sichtbar.
  >
  > During the active hours the user sets (weekly schedule), the Wear OS app detects whether the user walks a few
  > steps and counts this as a movement break. A foreground service of type "health" reads the step sensor (step
  > detector/counter) for this. It only runs while "Auto steps" is on and the physical activity permission is
  > granted, only within the active hours, and is visible as the ongoing notification "Step detection active".

- Auswirkung, wenn das System die Aufgabe verschiebt oder unterbricht: *Gehen würde nicht als Bewegungspause
  erkannt; die Uhr würde trotz Bewegung erinnern bzw. der Verlauf wäre unvollständig.*
- **Video-Link** (wird verlangt): kurzes, nicht gelistetes YouTube-Video, das zeigt: „Auto-Schritte" einschalten →
  laufende Benachrichtigung erscheint → ein paar Schritte gehen → Timer springt zurück / Pause im Verlauf.

### 2.12 Exakte Alarme (`SCHEDULE_EXACT_ALARM`)

Die Uhr-App deklariert nur `SCHEDULE_EXACT_ALARM` (ohne `maxSdkVersion`, also für alle Android-Versionen) und
**nicht** mehr `USE_EXACT_ALARM`. Eingeschränkt ist bei Google Play nur `USE_EXACT_ALARM` (Wecker-, Timer- und
Kalender-Apps, mit Erklärung in der Console). Für `SCHEDULE_EXACT_ALARM` ist **keine Erklärung zu eingeschränkten
Berechtigungen nötig** – die Erlaubnis erteilt die Nutzerin bzw. der Nutzer selbst. **Prüfen:** Fragt die Console
trotzdem nach exakten Alarmen, angeben, dass die Erlaubnis freiwillig ist und die App ohne sie funktioniert.

So verhält sich die App:

- **Wear OS 3 (Android 11):** keine Erlaubnis nötig, Alarme sind exakt.
- **Wear OS 4 (Android 13):** bei der Installation erteilt, lässt sich aber entziehen.
- **Ab Wear OS 5 (Android 14):** für neu installierte Apps standardmäßig **nicht** erteilt. Erlauben unter
  „Wecker und Erinnerungen" (engl. *Alarms & reminders*); die Startseite der Uhr bietet dafür den Eintrag „Exakte
  Alarme – Erlauben für pünktliche Erinnerungen".
- Ohne Erlaubnis plant die App **ungenaue Alarme**: Erinnerungen kommen dann eventuell einige Minuten später, fallen
  aber nicht aus. Wird die Erlaubnis erteilt, plant die App sofort exakt neu.

### 2.13 Weitere Berechtigungen

Keine weiteren eingeschränkten Berechtigungen (kein SMS/Anrufliste, kein Standort im Hintergrund, kein
Dateizugriff auf alles, kein `QUERY_ALL_PACKAGES`, keine Bedienungshilfen-API). `ACTIVITY_RECOGNITION` ist eine
normale Laufzeitberechtigung ohne eigenes Formular, muss aber in der Datenschutzerklärung erklärt sein (ist sie).
