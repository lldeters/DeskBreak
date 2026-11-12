import java.io.StringReader
import java.util.Properties

// Top-level build file: Plugins werden hier nur deklariert und in den Modulen angewendet.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

/*
 * Upload-Schlüssel für die Release-Builds von Uhr (:app) und Handy (:mobile), siehe
 * docs/play-store/signing-and-build.md.
 *
 * Beide Apps MÜSSEN mit demselben Schlüssel hochgeladen werden: Play App Signing signiert sie dann mit demselben
 * App-Signaturschlüssel, und nur dann stellt der Wearable Data Layer zwischen Uhr und Handy zu (gleiche
 * applicationId UND gleiche Signatur). Deshalb wird die Signatur hier einmal für alle App-Module eingerichtet
 * statt in jedem Modul getrennt.
 *
 * Quelle je Wert: zuerst die Umgebungsvariable (z. B. für CI), sonst keystore.properties im Wurzelverzeichnis
 * (nicht im Git, Vorlage: keystore.properties.example). Ein relativer Pfad zur Keystore-Datei gilt ab dem
 * Wurzelverzeichnis. Fehlt etwas, bleiben Release-Builds unsigniert (mit Warnung, ohne Abbruch); Debug- und
 * Benchmark-Builds signiert AGP ohnehin mit dem Debug-Schlüssel.
 */
val keystoreProperties = Properties().apply {
    providers.fileContents(layout.projectDirectory.file("keystore.properties")).asText.orNull
        ?.let { load(StringReader(it)) }
}

fun signingValue(environmentVariable: String, property: String): String? =
    (providers.environmentVariable(environmentVariable).orNull ?: keystoreProperties.getProperty(property))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/** Werte unter den Namen aus keystore.properties; `null` = weder als Umgebungsvariable noch in der Datei angegeben. */
val uploadSigning: Map<String, String?> = mapOf(
    "storeFile" to signingValue("DESKBREAK_KEYSTORE_FILE", "storeFile"),
    "storePassword" to signingValue("DESKBREAK_KEYSTORE_PASSWORD", "storePassword"),
    "keyAlias" to signingValue("DESKBREAK_KEY_ALIAS", "keyAlias"),
    "keyPassword" to signingValue("DESKBREAK_KEY_PASSWORD", "keyPassword"),
)
val missingSigning = uploadSigning.filterValues { it == null }.keys

/** Baut dieser Aufruf ein Release-Paket (z. B. bundleRelease, assembleRelease, assemble)? */
val buildsReleasePackage = gradle.startParameter.taskNames.any { task ->
    task.substringAfterLast(':').matches(Regex("(assemble|bundle|package|install)(Release.*)?"))
}

when {
    missingSigning.isEmpty() -> Unit
    // Teilweise angegeben: sicher ein Versehen, daher bei jedem Build melden.
    missingSigning.size < uploadSigning.size -> logger.warn(
        "DeskBreak: Upload-Schlüssel unvollständig, es fehlt ${missingSigning.joinToString()} " +
            "(keystore.properties bzw. DESKBREAK_*-Umgebungsvariablen). Release-Builds bleiben unsigniert.",
    )
    // Gar nicht angegeben: nur bei Release-Paketen melden (Debug-Builds und Tests brauchen keinen Upload-Schlüssel).
    buildsReleasePackage -> logger.warn(
        "DeskBreak: Kein Upload-Schlüssel (keystore.properties oder DESKBREAK_*-Umgebungsvariablen), " +
            "Release-Builds bleiben unsigniert. Für Google Play signieren: docs/play-store/signing-and-build.md.",
    )
}

subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            if (missingSigning.isEmpty()) {
                val upload = signingConfigs.create("upload") {
                    storeFile = rootProject.file(uploadSigning.getValue("storeFile")!!)
                    storePassword = uploadSigning.getValue("storePassword")
                    keyAlias = uploadSigning.getValue("keyAlias")
                    keyPassword = uploadSigning.getValue("keyPassword")
                }
                // Läuft beim Anwenden des Plugins, also vor dem android { }-Block des Moduls: Build-Typen, die
                // dort per initWith(release) entstehen, übernehmen die Signatur (oder setzen eine eigene).
                buildTypes.getByName("release").signingConfig = upload
            }
        }
    }
}
