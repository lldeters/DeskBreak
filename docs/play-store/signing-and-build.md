# Signieren und Bauen für Google Play

Diese Anleitung beschreibt, wie die beiden DeskBreak-Apps für Google Play gebaut und signiert werden: die
Handy-App (Modul `:mobile`) und die Uhr-App (Modul `:app`). Beide haben dieselbe applicationId
`dev.stepcron.wear` und gehören zu **einem** Play-Eintrag.

Kurzfassung für ein Release:

1. Einmalig: Upload-Schlüssel erstellen (Abschnitt 1) und `keystore.properties` anlegen (Abschnitt 2).
2. Version hochzählen: `deskbreak.versionCode` in `gradle.properties` um 1 erhöhen, `deskbreak.versionName`
   anpassen (Abschnitt 4).
3. Prüfen und bauen:
   ```bash
   ./gradlew :app:testDebugUnitTest :mobile:testDebugUnitTest :mobile:lintRelease :app:lintRelease
   ./gradlew :mobile:bundleRelease :app:bundleRelease
   ```
4. Hochladen: `mobile/build/outputs/bundle/release/mobile-release.aab` in die Handy-Tracks,
   `app/build/outputs/bundle/release/app-release.aab` in die Wear-OS-Tracks (Abschnitt 6).

---

## 1. Upload-Schlüssel erstellen (einmalig)

Google Play arbeitet mit **Play App Signing**: Man lädt Bundles hoch, die mit dem eigenen *Upload-Schlüssel*
signiert sind; Play prüft diese Signatur und signiert die ausgelieferten APKs mit dem *App-Signaturschlüssel*, den
Google verwahrt.

**Uhr und Handy verwenden denselben Upload-Schlüssel.** Ein Play-Eintrag hat genau einen Upload-Schlüssel und
genau einen App-Signaturschlüssel – damit sind beide Apps auf den Geräten gleich signiert. Das ist Pflicht für den
Wearable Data Layer: Die Play-Dienste stellen Nachrichten (Einstellungen, Verlauf, Lizenz) nur zwischen Apps mit
gleicher applicationId **und** gleicher Signatur zu.

Schlüssel erzeugen (Pfad außerhalb des Projekts wählen, `[Name]` ersetzen):

```bash
mkdir -p ~/keys
keytool -genkeypair -v \
  -keystore ~/keys/deskbreak-upload.jks \
  -storetype PKCS12 \
  -alias upload \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=[Name]"
```

- `keytool` fragt das Passwort ab. Bei PKCS12 (Standard) gilt dasselbe Passwort für Keystore und Schlüssel, also
  in `keystore.properties` zweimal dasselbe eintragen.
- `-validity 10000` sind gut 27 Jahre (Google empfiehlt mindestens 25).
- Keystore-Datei und Passwort sicher aufbewahren (z. B. Passwortmanager plus eine Offline-Kopie). Geht der
  Upload-Schlüssel verloren, kann man in der Play Console (App-Integrität → App-Signatur) einen neuen
  Upload-Schlüssel beantragen; dafür braucht man das Zertifikat des neuen Schlüssels als PEM:
  `keytool -export -rfc -keystore ~/keys/neu.jks -alias upload -file upload_certificate.pem`.
- Nie einchecken: `*.jks`, `*.keystore` und `keystore.properties` stehen in `.gitignore`.

## 2. keystore.properties oder Umgebungsvariablen

Die Signatur wird zentral in `build.gradle.kts` im Wurzelverzeichnis für **alle** App-Module eingerichtet (so
können Uhr und Handy nicht versehentlich verschieden signiert werden). Die Module selbst enthalten keine
Schlüsseldaten.

Lokal:

```bash
cp keystore.properties.example keystore.properties
# dann die vier Werte eintragen
```

```properties
storeFile=/home/[Name]/keys/deskbreak-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

`storeFile` darf absolut oder relativ zum Wurzelverzeichnis des Projekts sein.

Alternativ (z. B. in einer CI) über Umgebungsvariablen; sie haben Vorrang vor der Datei, auch einzeln:

| Umgebungsvariable             | entspricht      |
|-------------------------------|-----------------|
| `DESKBREAK_KEYSTORE_FILE`     | `storeFile`     |
| `DESKBREAK_KEYSTORE_PASSWORD` | `storePassword` |
| `DESKBREAK_KEY_ALIAS`         | `keyAlias`      |
| `DESKBREAK_KEY_PASSWORD`      | `keyPassword`   |

Verhalten:

- **Alles da:** `release` wird mit dem Upload-Schlüssel signiert (Signaturkonfiguration `upload`).
- **Nichts da:** Release-Builds bleiben unsigniert; beim Bauen eines Release-Pakets erscheint die Warnung
  „DeskBreak: Kein Upload-Schlüssel …". Der Build bricht nicht ab. Play nimmt unsignierte Bundles aber nicht an.
- **Nur teilweise da:** Warnung „Upload-Schlüssel unvollständig, es fehlt …" bei jedem Build, Release bleibt
  unsigniert.
- Debug-Builds und der Build-Typ `benchmark` der Uhr verwenden wie bisher den Debug-Schlüssel.

Hinweis: Warnungen zeigt Gradle mit `-q` nicht an – für Release-Builds daher ohne `-q` bauen.

Signatur prüfen (beide Ausgaben müssen denselben SHA-256-Fingerabdruck zeigen):

```bash
keytool -printcert -jarfile mobile/build/outputs/bundle/release/mobile-release.aab
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
```

## 3. Bauen

Bundles für Google Play (das wird hochgeladen):

```bash
./gradlew :mobile:bundleRelease :app:bundleRelease
```

| App   | Modul     | App Bundle (AAB)                                      |
|-------|-----------|-------------------------------------------------------|
| Handy | `:mobile` | `mobile/build/outputs/bundle/release/mobile-release.aab` |
| Uhr   | `:app`    | `app/build/outputs/bundle/release/app-release.aab`       |

APKs zum lokalen Ausprobieren der Release-Variante:

```bash
./gradlew :mobile:assembleRelease :app:assembleRelease
```

- signiert: `mobile/build/outputs/apk/release/mobile-release.apk`, `app/build/outputs/apk/release/app-release.apk`
- ohne Upload-Schlüssel: `…/mobile-release-unsigned.apk`, `…/app-release-unsigned.apk` (lassen sich nicht
  installieren)

Weitere Ausgaben:

- R8-Mapping (für lesbare Absturzberichte): `mobile/build/outputs/mapping/release/mapping.txt` und
  `app/build/outputs/mapping/release/mapping.txt`. Die Datei steckt auch im AAB; Play übernimmt sie beim Hochladen
  automatisch.
- Eigenen nativen Code gibt es nicht. Die Bundles enthalten nur zwei kleine Bibliotheken aus AndroidX
  (`libandroidx.graphics.path.so` von Compose, auf der Uhr zusätzlich `libdatastore_shared_counter.so` von
  DataStore). Meldet die Play Console fehlende native Debug-Symbole, ist das für diese Bibliotheken unkritisch.

Wichtig beim Testen des Abgleichs: Der Data Layer verbindet nur gleich signierte Apps. Eine aus Play installierte
App (Play-Signatur) spricht also nicht mit einer lokal installierten (Upload- oder Debug-Signatur). Lokal immer
beide Apps aus derselben Quelle installieren (beide Debug, beide Release mit Upload-Schlüssel, oder beide aus Play,
z. B. über einen internen Test-Track). Der Build-Typ `benchmark` der Uhr ist mit dem Debug-Schlüssel signiert und
gleicht sich deshalb nur mit einer Debug-Handy-App ab.

## 4. Versionen

Einzige Stelle: `gradle.properties`

```properties
deskbreak.versionName=1.0.0
deskbreak.versionCode=1
```

Beide Build-Skripte lesen die Werte über `providers.gradleProperty(...)`. Play verlangt für jedes hochgeladene
Bundle einen eigenen `versionCode` – über alle Formfaktoren des Eintrags hinweg. Daher:

| App   | versionCode                     | bei `deskbreak.versionCode=1` | bei `=2` |
|-------|---------------------------------|-------------------------------|----------|
| Handy | `deskbreak.versionCode * 10 + 1` | 11                            | 21       |
| Uhr   | `deskbreak.versionCode * 10 + 2` | 12                            | 22       |

`versionName` ist für beide gleich (z. B. `1.0.0`).

Ein Update veröffentlichen:

1. `deskbreak.versionCode` um 1 erhöhen (nie verringern, nie einen Wert wiederverwenden), `deskbreak.versionName`
   nach Bedarf anheben (z. B. `1.0.1` für Fehlerbehebungen, `1.1.0` für neue Funktionen).
2. Beide Bundles neu bauen. Wurde nur eine App geändert, reicht es, deren Bundle hochzuladen; der versionCode der
   anderen App bleibt dann einfach ungenutzt.
3. Für einmalige Builds lässt sich der Wert auch übergeben, ohne die Datei zu ändern:
   `./gradlew -Pdeskbreak.versionCode=7 :mobile:bundleRelease`.

Warum die Uhr die höhere Endziffer hat: Könnte ein Gerät beide Bundles installieren, liefert Play das mit dem
höheren versionCode aus. Die Uhr-App verlangt `android.hardware.type.watch`, kommt also nie aufs Handy; eine Uhr
bekommt bei gleicher Basis immer die Uhr-App.

## 5. Play App Signing

- Neue Apps auf Google Play nutzen Play App Signing verpflichtend. Beim ersten Hochladen den von Google erzeugten
  App-Signaturschlüssel wählen (Standard). Das erste mit dem Upload-Schlüssel signierte Bundle registriert diesen
  Schlüssel als Upload-Schlüssel des Eintrags.
- Uhr- und Handy-Bundle gehören zum selben Eintrag und bekommen deshalb automatisch denselben
  App-Signaturschlüssel – genau das braucht der Data Layer. Nichts weiter zu tun.
- Den SHA-256-Fingerabdruck des App-Signaturzertifikats zeigt die Play Console unter App-Integrität → App-Signatur
  (wird für DeskBreak derzeit nirgends eingetragen).
- Die von Play signierten APKs kann man im „App Bundle Explorer" herunterladen, um sie vor der Freigabe zu testen.

## 6. Uhr-App in der Play Console

Laut Android-Dokumentation („Package and distribute Wear OS apps"):

1. In der Play Console im Navigationsbereich **Test and release** (deutsch: „Testen und veröffentlichen") öffnen.
2. **Advanced settings** („Erweiterte Einstellungen") → Tab **Form factors** („Formfaktoren") → **Add form factor**
   → **Wear OS**, dann Wear-OS-Screenshots für den Store-Eintrag hinzufügen.
3. Das Uhr-Bundle (`app-release.aab`) in einen Wear-OS-Track hochladen; das Handy-Bundle (`mobile-release.aab`) in
   die normalen Tracks. Erst über einen geschlossenen Test, danach in den Erweiterten Einstellungen für Wear OS
   anmelden und die Wear-OS-Richtlinien bestätigen; dann veröffentlichen.
4. Der Store-Eintrag muss „Wear OS" erwähnen und mindestens einen Screenshot von der Uhr zeigen; Kachel und
   Komplikation im Eintrag erwähnen.

## 7. Geprüft für targetSdk 35 (Uhr) und 36 (Handy)

Anforderung von Google Play ab 31.08.2026 (developer.android.com, „Target API level requirements"): neue Apps und
Updates müssen API 36 (Android 16) anvisieren, Wear-OS-Apps mindestens API 35 (Android 15). Eine Verlängerung bis
01.11.2026 kann beantragt werden. Daher:

- Handy (`:mobile`): `targetSdk = 36`
- Uhr (`:app`) und Baseline-Profil-Generator (`:baselineprofile`): `targetSdk = 35` (Wear OS 5.1 basiert auf
  Android 15)
- `compileSdk` ist bei allen Modulen 36.

Durchgesehen wurden die Verhaltensänderungen für Apps mit targetSdk 35 (Android 15) und 36 (Android 16) sowie die
Wear-OS-5/5.1-Hinweise, jeweils gegen den Code:

### Uhr (targetSdk 35)

| Änderung (Android 15) | Betrifft DeskBreak? | Ergebnis |
|---|---|---|
| `BOOT_COMPLETED`-Empfänger dürfen bestimmte Foreground-Services nicht mehr starten (dataSync, camera, mediaPlayback, phoneCall, mediaProjection, microphone) | `BootReceiver` stellt den Timer wieder her und kann die Schritterkennung starten | Nicht betroffen: `StepTrackerService` hat den Typ `health`, der nicht auf der Liste steht. |
| Zeitlimit 6 h/Tag für FGS-Typen dataSync und mediaProcessing | Nein | Typ `health` hat kein solches Limit. |
| FGS-Start mit `SYSTEM_ALERT_WINDOW` | Nein | Berechtigung wird nicht verwendet. |
| Exakte Alarme | Ja (Erinnerungs-Timer) | Unverändert gegenüber targetSdk 34: `SCHEDULE_EXACT_ALARM` bis API 32, `USE_EXACT_ALARM` ab API 33, ungenauer Fallback im `AlarmScheduler`. Exakte Alarme sind vom Startverbot für Foreground-Services aus dem Hintergrund ausgenommen. Siehe aber den Play-Hinweis unten. |
| OpenJDK: `List.removeFirst()/removeLast()` (API 35) statt Kotlin-Erweiterung | `StepBurstDetector` ruft `removeFirst()` auf | Unkritisch: `stepTimes` ist `kotlin.collections.ArrayDeque`, die Methode gehört zur Klasse selbst. Lint `NewApi` meldet nichts. |
| OpenJDK: strengere Prüfung von `String.format` | Formatmuster wie `%02d`, `%d:%02d`, `%.1f` | Alle gültig. |
| Edge-to-Edge, stabile Konfiguration (`screenWidthDp` inkl. Systemleisten) | Wear OS hat keine Status-/Navigationsleiste wie das Handy | Keine Auswirkung. |
| Hintergrund-Activity-Starts / PendingIntents | Benachrichtigungen, Kachel, Komplikation | Alle PendingIntents sind explizit und `FLAG_IMMUTABLE`; Activities öffnen nur nach Tippen des Nutzers. |
| Bekanntes Wear-OS-5.1-Problem: `SecurityException` mit `androidx.wear.tiles` < 1.5.0-alpha05 bei targetSdk 35 | Kachel | Projekt nutzt Tiles 1.5.0 – behoben. |
| Predictive Back | Uhr nutzt Wischen zum Schließen | `enableOnBackInvokedCallback` bleibt ungesetzt (Standard bei targetSdk 35: aus); Wear OS 5.1 zeigt die Animationen ohnehin nicht zuverlässig. |
| Nicht stören (DND) global ändern | Die Uhr liest nur den Filter („Ruhemodi beachten") | Nicht betroffen. |
| `elegantTextHeight`, TextView-Breite, Audio-Fokus, TLS 1.0/1.1 | Nein | Nur Englisch/Deutsch, keine Audio-Wiedergabe, kein eigenes Netzwerk. |

### Handy (targetSdk 36)

| Änderung | Betrifft DeskBreak? | Ergebnis |
|---|---|---|
| Edge-to-Edge erzwungen (15), Abschalten nicht mehr möglich (16) | Ja | `MainActivity` war schon edge-to-edge (`enableEdgeToEdge()`); `Scaffold`, `TopAppBar` und `NavigationBar` (Material 3) berücksichtigen die Insets. Kein `statusBarColor`/`navigationBarColor` im Theme, kein `windowOptOutEdgeToEdgeEnforcement`. |
| Edge-to-Edge auch in Dialogfenstern (`setDecorFitsSystemWindows` wirkungslos) | `ScheduleTimeDialog` (eigener Dialog über die volle Breite, mit Tastatureingabe) | **Geändert:** Der Dialog hält jetzt per `windowInsetsPadding(WindowInsets.safeDrawing)` Abstand zu Systemleisten, Kamera-Aussparung und Tastatur. Die `AlertDialog`s sind schwebende Fenster und nicht betroffen. Neue Dialoge (z. B. Kaufdialog) genauso behandeln. |
| Predictive Back standardmäßig an, `onBackPressed`/`KEYCODE_BACK` entfallen (16) | Navigation per Navigation Compose | Kein `onBackPressed` und kein `KEYCODE_BACK` im Code; Navigation Compose 2.8 und Activity 1.9 nutzen den `OnBackPressedDispatcher`. `android:enableOnBackInvokedCallback="true"` ist jetzt ausdrücklich gesetzt (gleiches Verhalten auch auf Android 13–15). |
| Große Bildschirme (≥ 600 dp): Ausrichtung, Größenänderung und Seitenverhältnis werden ignoriert (16) | Tablets, Foldables | Die App setzt keine dieser Einschränkungen (`screenOrientation`, `resizeableActivity`, Seitenverhältnis), also keine Änderung. Quer- und Tablet-Layout auf einem Gerät ansehen. |
| Stabile Konfiguration (15) | `ScheduleTimeDialog` liest `screenWidthDp/screenHeightDp` | Nur für „hochkant oder quer" – mit Systemleisten im Maß unverändert richtig. |
| Feste Taktung `scheduleAtFixedRate` (16) | Nein | Nicht verwendet. |
| Gesundheitsberechtigungen statt `BODY_SENSORS` (16) | Nein | Das Handy liest keine Sensoren; die Uhr nutzt `ACTIVITY_RECOGNITION` (nicht betroffen) und bleibt bei targetSdk 35. |
| Safer Intents / `intentMatchingFlags` (16, opt-in) | `DeskBreakListenerService` | Nicht aktiviert; der Dienst wird von den Play-Diensten mit passenden Filtern gebunden. |
| Lokales-Netzwerk-Schutz, MediaStore-Version, `elegantTextHeight` (16) | Nein | Kein lokales Netzwerk, kein MediaStore (Export über den System-Dateiauswahldialog), nur Englisch/Deutsch. |
| 16-KB-Seitengröße (Play-Pflicht für Apps mit targetSdk ≥ 35) | Nur native Bibliotheken aus AndroidX (Compose, DataStore) | Erfüllt (beide Apps): ELF-Segmente auf 16 KB ausgerichtet (`readelf -l`: Align `0x4000`), im APK 16-KB-ausgerichtet (`zipalign -c -P 16 -v 4`: „Verification successful"). |

### Hinweise für die Play Console (Berechtigungen der Uhr)

- **`USE_EXACT_ALARM`** ist laut Play-Richtlinie Wecker-, Timer- und Kalender-Apps vorbehalten; in der Play
  Console wird dafür eine Erklärung verlangt. DeskBreak ist ein Erinnerungs-Timer; lehnt Play das ab, die
  Berechtigung entfernen und `SCHEDULE_EXACT_ALARM` ohne `maxSdkVersion` deklarieren (Nutzer muss „Wecker und
  Erinnerungen" erlauben; die App fällt bis dahin auf ungenaue Alarme zurück und bietet den Weg in die Einstellung
  schon an).
- **`FOREGROUND_SERVICE_HEALTH`**: Play verlangt eine Erklärung zum Foreground-Service-Typ (Beschreibung und kurzes
  Video, wie die Schritterkennung während der aktiven Zeit läuft).
- **`ACTIVITY_RECOGNITION`** und Gesundheitsbezug: Angaben im Abschnitt „App-Inhalte" (u. a. Datensicherheit,
  ggf. Gesundheits-Apps) passend ausfüllen.

## 8. Sprachen

- Ausgeliefert werden nur Englisch (`values/`) und Deutsch (`values-de/`): `androidResources.localeFilters` in
  beiden Modulen entfernt die übrigen Übersetzungen der Bibliotheken, damit keine fremdsprachigen Texte erscheinen.
- Sprache pro App (Android 13+, Einstellungen → Apps → DeskBreak → Sprache): `res/xml/locales_config.xml` und
  `android:localeConfig` in beiden Apps; ob Wear OS die Einstellung anbietet, hängt vom System ab.
- Sprach-Splits im Bundle sind abgeschaltet (`bundle.language.enableSplit = false`): Beide Sprachen sind immer
  installiert (wenige KB), ein Sprachwechsel funktioniert sofort und auch offline.
- Neue Sprache: Ordner `values-xx/` anlegen und `xx` in `localeFilters` **und** `locales_config.xml` beider Apps
  ergänzen.

## 9. Sicherung (Auto Backup, Gerätewechsel)

Beide Apps sichern ihre Daten über die Android-Sicherung (Google-Konto) und beim Umzug auf ein neues Gerät:

- `android:dataExtractionRules="@xml/data_extraction_rules"` (ab Android 12 / API 31) und
  `android:fullBackupContent="@xml/backup_rules"` (bis Android 11 / API 30). Beide Dateien je App gleich halten.
- Gesichert wird alles außer ausdrücklich ausgeschlossenen Caches – also Einstellungen, Verlauf (Uhr: Room-Datenbank
  `deskbreak.db`, DataStore `deskbreak_settings`) und die Lizenz- bzw. Testphasen-Daten, auch wenn deren Speicher
  später hinzukommt. Damit startet die Testphase nach Neuinstallation mit Wiederherstellung nicht neu.
- Ausgeschlossen: auf dem Handy der Snapshot-Cache der Uhr (`files/watch-snapshot.bin` und `.tmp`, kommt beim
  nächsten Abgleich neu), in beiden Apps die gerätebezogenen Merker von ProfileInstaller (`files/profileInstalled`,
  `files/profileinstaller_profileWrittenFor_lastUpdateTime.dat`). `cache/`, `code_cache/` und `no_backup/` sichert
  Android nie.
- Wer neue Dateien anlegt, die nur ein Cache sind, trägt sie in beide XML-Dateien der App ein (oder legt sie in
  `cacheDir`/`noBackupFilesDir`).

Testen (Gerät oder Emulator mit Google-Konto):

```bash
adb shell bmgr backupnow dev.stepcron.wear
adb uninstall dev.stepcron.wear
adb install mobile/build/outputs/apk/release/mobile-release.apk   # Wiederherstellung läuft bei der Installation
```

## 10. Release-Prüfung (Stand dieser Einrichtung)

- `./gradlew :mobile:bundleRelease :app:bundleRelease :mobile:assembleRelease :app:assembleRelease` läuft durch –
  ohne Schlüssel (unsignierte Ausgaben, Warnung wie oben) und mit einem Wegwerf-Schlüssel über die
  `DESKBREAK_*`-Umgebungsvariablen bzw. über `keystore.properties`. Signiert tragen beide APKs und beide AABs
  dasselbe Zertifikat; `:app:assembleBenchmark` ist weiterhin mit dem Debug-Schlüssel signiert.
- Ergebnis laut `aapt2 dump badging`: Handy `versionCode 11`, `targetSdkVersion 36`; Uhr `versionCode 12`,
  `targetSdkVersion 35`; beide `versionName 1.0.0`, Sprachen nur Standard (Englisch) und `de`.
- R8: keine Warnungen zu fehlenden Klassen, keine `missing_rules.txt`.
- `./gradlew :mobile:lintRelease :app:lintRelease`: 0 Fehler. Übrig sind Warnungen `UnusedAttribute` für
  `localeConfig` und `enableOnBackInvokedCallback` (gewollt: ältere Android-Versionen ignorieren die Attribute
  einfach) und auf der Uhr Hinweise auf neuere Bibliotheksversionen (`GradleDependency`, bewusst festgehalten,
  siehe `gradle/libs.versions.toml`).
- `./gradlew :app:testDebugUnitTest :mobile:testDebugUnitTest`: grün, ebenso die Screenshot-Tests der Handy-App
  (`-Pscreenshots`, Robolectric mit SDK 34, auch die deutschen Varianten).
- Noch offen, nur auf echten Geräten prüfbar: Zeit-Dialog quer und mit Tastatureingabe auf Android 15/16, Predictive
  Back auf Android 16, Tablet/Foldable-Layout, Sicherung und Wiederherstellung auf Uhr und Handy.
