# Datenschutzerklärung / Privacy Policy – DeskBreak

> **Hinweis für die Veröffentlichung (nicht mit veröffentlichen):** Die Platzhalter `[Name]`, `[Anschrift]`,
> `[E-Mail]` und `[Datum]` ausfüllen. Gehostet wird die Fassung aus
> [privacy-policy.html](privacy-policy.html) (gleicher Inhalt, eine Seite, beide Sprachen); diese Markdown-Datei
> dient zum Lesen und Ändern. Bei Änderungen an Berechtigungen (AndroidManifest.xml von `app` und `mobile`), an
> der Synchronisierung oder an den Käufen beide Dateien anpassen. Stand der Angaben: Code-Stand vom 12.11.2026.
> Alle Platzhalter im Überblick: [README.md, „Vor der Veröffentlichung ausfüllen"](README.md#vor-der-veröffentlichung-ausfüllen).
> Keine Rechtsberatung – im Zweifel von einer Fachperson prüfen lassen.

[Deutsch](#deutsch) · [English](#english)

---

## Deutsch

**Datenschutzerklärung für die App DeskBreak (Wear-OS-Uhr und Handy-App)**
Stand: [Datum]

### 1. Verantwortlicher

[Name]
[Anschrift]
E-Mail: [E-Mail]

### 2. Das Wichtigste in Kürze

- DeskBreak speichert deine Daten **nur auf deinen eigenen Geräten** (Uhr und, falls installiert, Handy).
- Es gibt **keine Server des Entwicklers**, **kein Benutzerkonto**, **keine Werbung** und **keine Analyse- oder
  Tracking-Werkzeuge**. Wir erfahren nicht, wie du die App nutzt.
- Uhr und Handy gleichen sich ausschließlich untereinander über die Google Play-Dienste ab.
- Käufe wickelt **Google Play** ab; Google verarbeitet die dafür nötigen Daten in eigener Verantwortung
  (Abschnitt 5). Zahlungsdaten erhalten wir nicht.

### 3. Welche Daten die App auf deinen Geräten verarbeitet

| Daten | Zweck | Wo gespeichert |
|---|---|---|
| Einstellungen (Intervall, Wochenplan, Snooze-Dauer, Vibration, Ruhemodi, Trageerkennung, Schritt-Schwelle, Tagesziel, Pausen) | Erinnerungen so planen, wie du es eingestellt hast | Uhr; Kopie auf dem Handy |
| Verlauf: Zeitpunkte von Bewegungspausen und Erinnerungen (erledigt, automatisch erkannt, „Jetzt bewegt", Snooze, verpasst) mit dem jeweils eingestellten Intervall | Auswertung, Tagesziel, Statistiken, Export | Uhr; die letzten 400 Tage als Kopie auf dem Handy |
| Status der Erinnerungen (nächste Erinnerung, Snooze, Grund der zuletzt still ausgelassenen Erinnerung) | Anzeige auf Uhr, Kachel, Komplikation und Handy | Uhr; Kopie auf dem Handy |
| Lizenzstatus (Beginn der Testphase, Art der Freischaltung, Zeitpunkt der letzten Prüfung bei Google Play) | Testphase und Freischaltung auf beiden Geräten | Uhr und Handy |

**Schritterkennung:** Während deiner aktiven Zeiten wertet die Uhr den Schrittsensor (Schrittdetektor bzw.
Schrittzähler) aus, um zu erkennen, ob du einige Schritte gegangen bist (z. B. 30 Schritte innerhalb von
2 Minuten). Gespeichert wird nur, *dass* eine Bewegungspause erkannt wurde – keine Schrittzahlen, keine
Bewegungsprofile. Die Funktion lässt sich abschalten.

**Trageerkennung und Ruhemodi:** Bevor die Uhr erinnert, prüft sie auf Wunsch, ob sie getragen wird
(Off-Body-Sensor), und ob „Nicht stören", Schlafenszeit oder Kinomodus aktiv sind. Diese Werte werden nicht
gespeichert; vermerkt wird höchstens, aus welchem Grund die letzte Erinnerung ausgelassen wurde.

DeskBreak verarbeitet **keine** Standortdaten, keine Kontakte, keine Gesundheitswerte wie Puls und keine
Werbe-IDs.

Die Daten bleiben gespeichert, bis du sie in der App löschst, eine Sicherung zurückspielst oder die App
deinstallierst.

### 4. Abgleich zwischen Uhr und Handy

Uhr und Handy tauschen Einstellungen, Status, Verlauf, Lizenzstatus und – wenn du es auslöst – Sicherungen über
die **Wearable Data Layer API der Google Play-Dienste** aus. Zugestellt wird nur an DeskBreak auf deinen eigenen,
gekoppelten Geräten (gleiche App, gleiche Signatur). Die Übertragung läuft per verschlüsselter
Bluetooth-Verbindung oder, wenn Bluetooth nicht verfügbar ist, Ende-zu-Ende-verschlüsselt über Server von Google.
Der Entwickler hat darauf keinen Zugriff.

### 5. Käufe über Google Play

Nach der kostenlosen Testphase kannst du DeskBreak per Einmalkauf oder Monatsabo freischalten. Der Kauf läuft
vollständig über **Google Play** (Google Play Billing); deine Zahlungsdaten verarbeitet nur Google nach der
[Datenschutzerklärung von Google](https://policies.google.com/privacy). Die App erfährt von Google Play nur, ob
eine gültige Freischaltung besteht, und gibt diese Information an dein jeweils anderes Gerät weiter (siehe
Abschnitt 4).

Für Käufe und um beim Öffnen zu prüfen, ob ein Kauf besteht, ist die Bibliothek Google Play Billing in die App
eingebunden. Sie arbeitet über den Google Play Store und die Google Play-Dienste auf deinem Gerät; diese tauschen
dabei technische Daten (z. B. Geräte-, App- und Versionsangaben) und Kaufdaten (z. B. Produkt, Kaufzeitpunkt,
Status eines Abos) mit Google aus. Dafür ist Google eigenständig verantwortlich und verarbeitet die Daten nach der
[Datenschutzerklärung von Google](https://policies.google.com/privacy) und den Bedingungen von Google Play. Der
Entwickler erhält diese Daten nicht und auch keine Zahlungsdaten.

Google stellt uns für Abrechnung, Steuern und Erstattungen Bestellinformationen bereit (z. B. Bestellnummer,
Datum, Produkt, Preis und Land des Kaufs) – keine Zahlungsdaten wie Karten- oder Kontonummern. Wir nutzen sie nur
für diese Zwecke (Art. 6 Abs. 1 lit. b und c DSGVO) und bewahren sie so lange auf, wie es steuerrechtlich
vorgeschrieben ist (in Deutschland bis zu 10 Jahre).

### 6. Sicherung durch Android

DeskBreak erlaubt die Datensicherung von Android. Hast du die Sicherung in den Einstellungen deines Geräts
eingeschaltet, kann Android die App-Daten (Einstellungen, Verlauf, Lizenzstatus) in die Sicherung deines
Google-Kontos aufnehmen und bei einem neuen Gerät wiederherstellen. Diese Sicherung verwaltet Google; der
Entwickler hat keinen Zugriff. Du kannst sie in den Android-Einstellungen abschalten und in deinem Google-Konto
löschen.

### 7. Export und Sicherungsdateien

Nur wenn du es auslöst, erstellt die Handy-App eine CSV-Datei oder eine Sicherungsdatei. Wo die Datei gespeichert
wird, wählst du im Dateidialog von Android (z. B. im Gerätespeicher oder bei einem Cloud-Dienst deiner Wahl).
Für dort gespeicherte Dateien gelten die Bedingungen des gewählten Speicherorts. Beim Wiederherstellen liest die
App nur die Datei, die du auswählst.

### 8. Berechtigungen

**Uhr:**

| Berechtigung | Wofür |
|---|---|
| Benachrichtigungen (`POST_NOTIFICATIONS`) | Erinnerungen und die laufende Anzeige der Schritterkennung |
| Körperliche Aktivität (`ACTIVITY_RECOGNITION`) | Schrittsensor für die automatische Schritterkennung |
| Vibration (`VIBRATE`) | Vibration bei Erinnerungen |
| Exakte Alarme (`SCHEDULE_EXACT_ALARM`, in den Einstellungen „Wecker und Erinnerungen"; erteilst du selbst) | pünktliche Erinnerungen; ohne diese Erlaubnis erinnert die Uhr unter Umständen einige Minuten später |
| Start nach dem Hochfahren (`RECEIVE_BOOT_COMPLETED`), Wach halten (`WAKE_LOCK`) | Erinnerungen nach Neustart oder Update weiter planen und zuverlässig auslösen |
| Vordergrunddienst (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`) | Schritterkennung während der aktiven Zeit, sichtbar als laufende Benachrichtigung |
| Google Play-Abrechnung (`com.android.vending.BILLING`) | Käufe über Google Play |
| Internet, Netzwerkstatus (`INTERNET`, `ACCESS_NETWORK_STATE`) | benötigt von Google Play-Abrechnung und Google Play-Diensten; DeskBreak selbst verbindet sich mit keinem eigenen Server |

Für Trageerkennung und Ruhemodi braucht die App keine eigene Berechtigung.

**Handy:** Google Play-Abrechnung, Internet und Netzwerkstatus (wie oben). Dateien liest und schreibt die App nur
über den Dateidialog von Android; eine Speicherberechtigung ist nicht nötig.

### 9. Absturzberichte

DeskBreak enthält keine eigene Fehler- oder Absturzberichterstattung. Wenn du in Android der Weitergabe von
Nutzungs- und Diagnosedaten an Google zugestimmt hast, kann Android Absturzberichte an Google senden; wir sehen
diese in der Google Play Console nur zusammengefasst und ohne Bezug zu deiner Person.

### 10. Keine Weitergabe, keine Werbung

Wir geben keine Daten an Dritte weiter, verkaufen keine Daten, zeigen keine Werbung und erstellen keine Profile.
Was Google für Abgleich, Käufe und Sicherung in eigener Verantwortung verarbeitet, beschreiben die Abschnitte 4
bis 6.

### 11. Kinder

DeskBreak richtet sich nicht an Kinder unter 16 Jahren.

### 12. Deine Rechte

Du hast nach der DSGVO das Recht auf Auskunft, Berichtigung, Löschung, Einschränkung der Verarbeitung,
Datenübertragbarkeit und Widerspruch sowie das Recht, dich bei einer Datenschutz-Aufsichtsbehörde zu beschweren.
Da die App-Daten nur auf deinen Geräten liegen, kannst du sie dort jederzeit selbst einsehen, exportieren und
löschen. Zu Bestellinformationen (Abschnitt 5) und allen anderen Fragen erreichst du uns unter [E-Mail].

### 13. Daten löschen

Deinstallierst du DeskBreak, löscht Android alle App-Daten auf dem jeweiligen Gerät. Exportierte Dateien bleiben
dort, wo du sie gespeichert hast. Eine Android-Sicherung löschst du in den Einstellungen deines Google-Kontos.

### 14. Änderungen

Ändert sich die App so, dass diese Erklärung nicht mehr stimmt, passen wir sie an. Es gilt die jeweils unter
dieser Adresse veröffentlichte Fassung.

---

## English

**Privacy policy for the DeskBreak app (Wear OS watch and phone app)**
Last updated: [Datum]

### 1. Controller

[Name]
[Anschrift]
Email: [E-Mail]

### 2. Summary

- DeskBreak stores your data **only on your own devices** (your watch and, if installed, your phone).
- There are **no developer servers**, **no user account**, **no ads** and **no analytics or tracking tools**. We
  do not learn how you use the app.
- Watch and phone sync only with each other via Google Play services.
- Purchases are handled by **Google Play**; Google processes the data needed for this under its own
  responsibility (section 5). We never receive your payment details.

### 3. Data the app processes on your devices

| Data | Purpose | Stored on |
|---|---|---|
| Settings (interval, weekly schedule, snooze length, vibration, quiet modes, wrist detection, step threshold, daily goal, pauses) | Scheduling reminders the way you set them up | Watch; copy on the phone |
| History: times of movement breaks and reminders (done, detected automatically, "Just moved", snoozed, missed) with the interval set at the time | Stats, daily goal, charts, export | Watch; the last 400 days as a copy on the phone |
| Reminder status (next reminder, snooze, reason for the last silently skipped reminder) | Display on the watch, tile, complication and phone | Watch; copy on the phone |
| License status (start of the trial, type of unlock, time of the last check with Google Play) | Trial and unlock on both devices | Watch and phone |

**Step detection:** During your active hours the watch reads the step sensor (step detector or step counter) to
notice whether you walked a few steps (for example 30 steps within 2 minutes). Only the fact *that* a movement
break was detected is stored – no step counts and no movement profiles. You can turn this feature off.

**Wrist detection and quiet modes:** Before reminding you, the watch can check whether it is being worn
(off-body sensor) and whether Do not disturb, bedtime or theater mode is on. These values are not stored; at most
the app remembers why the last reminder was skipped.

DeskBreak does **not** process location data, contacts, health readings such as heart rate, or advertising IDs.

The data is kept until you delete it in the app, restore a backup or uninstall the app.

### 4. Sync between watch and phone

Watch and phone exchange settings, status, history, license status and – when you start it – backups via the
**Wearable Data Layer API of Google Play services**. Data is delivered only to DeskBreak on your own paired devices
(same app, same signature). It travels over an encrypted Bluetooth connection or, when Bluetooth is not available,
end-to-end encrypted through Google's servers. The developer has no access to it.

### 5. Purchases through Google Play

After the free trial you can unlock DeskBreak with a one-time purchase or a monthly subscription. The purchase is
handled entirely by **Google Play** (Google Play Billing); your payment details are processed only by Google under
[Google's privacy policy](https://policies.google.com/privacy). The app only learns from Google Play whether a
valid unlock exists and passes this information on to your other device (see section 4).

To make purchases and to check, when you open the app, whether a purchase exists, the app includes the Google Play
Billing library. It works through the Google Play Store and Google Play services on your device, which exchange
technical data (such as device, app and version information) and purchase data (such as the product, time of
purchase and subscription status) with Google. Google is an independent controller for this and processes the data
under [Google's privacy policy](https://policies.google.com/privacy) and the Google Play terms. The developer
receives neither this data nor your payment details.

For accounting, taxes and refunds Google provides us with order information (such as order number, date,
product, price and country of purchase) – no payment details such as card or account numbers. We use it only for
these purposes (Art. 6(1)(b) and (c) GDPR) and keep it as long as tax law requires (in Germany up to 10 years).

### 6. Android backup

DeskBreak allows Android's data backup. If you turned on backup in your device settings, Android may include the
app data (settings, history, license status) in the backup of your Google account and restore it on a new
device. This backup is managed by Google; the developer has no access. You can turn it off in the Android settings
and delete it in your Google account.

### 7. Export and backup files

Only when you start it, the phone app creates a CSV file or a backup file. You choose where the file is saved in
Android's file dialog (for example on the device or with a cloud service of your choice). Files saved there are
subject to the terms of that location. When restoring, the app reads only the file you select.

### 8. Permissions

**Watch:**

| Permission | Used for |
|---|---|
| Notifications (`POST_NOTIFICATIONS`) | Reminders and the ongoing step detection notice |
| Physical activity (`ACTIVITY_RECOGNITION`) | Step sensor for automatic step detection |
| Vibration (`VIBRATE`) | Vibrating on reminders |
| Exact alarms (`SCHEDULE_EXACT_ALARM`, "Alarms & reminders" in the settings; granted by you) | On-time reminders; without this permission the watch may remind you a few minutes late |
| Run at startup (`RECEIVE_BOOT_COMPLETED`), keep awake (`WAKE_LOCK`) | Rescheduling reminders after a restart or update and firing them reliably |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`) | Step detection during active hours, visible as an ongoing notification |
| Google Play billing (`com.android.vending.BILLING`) | Purchases through Google Play |
| Internet, network state (`INTERNET`, `ACCESS_NETWORK_STATE`) | Required by Google Play billing and Google Play services; DeskBreak itself does not connect to any server of its own |

Wrist detection and quiet modes need no separate permission.

**Phone:** Google Play billing, internet and network state (as above). The app reads and writes files only
through Android's file dialog; no storage permission is needed.

### 9. Crash reports

DeskBreak contains no crash or error reporting of its own. If you agreed in Android to share usage and diagnostics
data with Google, Android may send crash reports to Google; we only see them in the Google Play Console in
aggregated form and without reference to you.

### 10. No sharing, no ads

We do not share or sell any data, show no ads and build no profiles. What Google processes under its own
responsibility for sync, purchases and backup is described in sections 4 to 6.

### 11. Children

DeskBreak is not directed at children under 16.

### 12. Your rights

Under the GDPR you have the right of access, rectification, erasure, restriction of processing, data portability
and objection, and the right to lodge a complaint with a data protection supervisory authority. Since the app data
is stored only on your devices, you can view, export and delete it there at any time. For order information
(section 5) and any other questions, contact us at [E-Mail].

### 13. Deleting data

When you uninstall DeskBreak, Android deletes all app data on that device. Exported files stay where you saved
them. You can delete an Android backup in your Google account settings.

### 14. Changes

If the app changes in a way that makes this policy inaccurate, we will update it. The version published at this
address applies.
