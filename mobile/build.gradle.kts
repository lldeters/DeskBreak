// Handy-App (Begleit-App): Verlauf und Auswertung auf dem großen Bildschirm, Einstellungen der Uhr,
// Export und Sicherung. Gleiche applicationId wie die Uhr – sonst stellt der Wearable Data Layer nichts zu.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.stepcron.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.stepcron.wear"
        minSdk = 26
        // Google Play verlangt für Handy-Apps ab 31.08.2026 API 36 (Android 16). Geprüfte Verhaltensänderungen
        // (Edge-to-Edge, Predictive Back, große Bildschirme): docs/play-store/signing-and-build.md.
        targetSdk = 36
        // Version aus gradle.properties (für Uhr und Handy gemeinsam). Jedes Bundle braucht bei Play einen
        // eigenen versionCode: Handy = Basis * 10 + 1, Uhr = Basis * 10 + 2.
        versionCode = providers.gradleProperty("deskbreak.versionCode").get().toInt() * 10 + 1
        versionName = providers.gradleProperty("deskbreak.versionName").get()
    }

    androidResources {
        // Nur die Sprachen der App ausliefern (Englisch, Deutsch; passend zu res/xml/locales_config.xml). Sonst
        // kämen die Übersetzungen der Bibliotheken mit, und bei anderer Gerätesprache (z. B. Französisch) mischten
        // sich deren Texte in die englische Oberfläche.
        localeFilters += listOf("en", "de")
    }

    bundle {
        // Beide Sprachen immer installieren statt als Sprach-Split nur die Gerätesprache: wenige KB, und die
        // Sprachwahl pro App (Android 13+) funktioniert sofort und offline.
        language {
            enableSplit = false
        }
    }

    buildTypes {
        // Signatur mit dem gemeinsamen Upload-Schlüssel von Uhr und Handy (falls vorhanden): siehe
        // build.gradle.kts im Wurzelverzeichnis.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Für die Screenshot-Tests (Robolectric braucht die Ressourcen der App).
        unitTests.isIncludeAndroidResources = true
    }
}

// Screenshots der Bildschirme (Robolectric, ohne Gerät): ./gradlew :mobile:testDebugUnitTest -Pscreenshots
// schreibt PNGs nach mobile/build/screenshots/. Ohne die Eigenschaft laufen sie nicht mit (langsam, lädt beim
// ersten Mal das Android-Abbild für Robolectric).
tasks.withType<Test>().configureEach {
    if (project.hasProperty("screenshots")) {
        systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
        outputs.upToDateWhen { false }
    } else {
        exclude("**/screenshots/**")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":billing"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
