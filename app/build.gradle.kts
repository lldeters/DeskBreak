plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "dev.stepcron.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.stepcron.wear"
        minSdk = 30 // Wear OS 3+
        // Google Play verlangt für Wear-OS-Apps ab 31.08.2026 mindestens API 35 (Wear OS 5.1). Geprüfte
        // Verhaltensänderungen: docs/play-store/signing-and-build.md.
        targetSdk = 35
        // Version aus gradle.properties (für Uhr und Handy gemeinsam). Jedes Bundle braucht bei Play einen
        // eigenen versionCode: Uhr = Basis * 10 + 2, Handy = Basis * 10 + 1.
        versionCode = providers.gradleProperty("deskbreak.versionCode").get().toInt() * 10 + 2
        versionName = providers.gradleProperty("deskbreak.versionName").get()
    }

    androidResources {
        // Nur die Sprachen der App ausliefern (Englisch, Deutsch; passend zu res/xml/locales_config.xml). Sonst
        // kämen die Übersetzungen der Bibliotheken mit, und bei anderer Gerätesprache (z. B. Französisch) mischten
        // sich deren Texte in die englische Oberfläche.
        localeFilters += listOf("en", "de")
    }

    bundle {
        // Beide Sprachen immer installieren statt als Sprach-Split nur die Gerätesprache: wenige KB, und ein
        // Sprachwechsel (auch pro App) funktioniert sofort und offline.
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Wie release (R8, nicht debuggable, Baseline-Profile), aber mit dem Debug-Schlüssel signiert,
        // damit sich die optimierte App lokal installieren lässt: ./gradlew installBenchmark.
        // Flüssigkeit nur mit dieser Variante beurteilen – der Debug-Build ist um ein Vielfaches
        // langsamer. Nur für Tests, nicht verteilen.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            // Erlaubt Profiler/Perfetto-Messungen ohne Debuggable-Overhead.
            isProfileable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Für die Store-Screenshots (Robolectric braucht die Ressourcen der App).
        unitTests.isIncludeAndroidResources = true
    }
}

// Screenshots der Uhr für den Play Store (Robolectric, ohne Gerät):
// ./gradlew :app:testDebugUnitTest -Pscreenshots schreibt PNGs nach app/build/screenshots/. Ohne die Eigenschaft
// laufen sie nicht mit (langsam, lädt beim ersten Mal das Android-Abbild für Robolectric).
tasks.withType<Test>().configureEach {
    if (project.hasProperty("screenshots")) {
        systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
        outputs.upToDateWhen { false }
    } else {
        exclude("**/screenshots/**")
    }
}

baselineProfile {
    // Ein Profil für alle Varianten (auch „benchmark") statt nur für release:
    // src/main/generated/baselineProfiles/, zusätzlich zum handgeschriebenen src/main/baseline-prof.txt.
    mergeIntoMain = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Jetpack Compose for Wear OS
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.wear.ongoing)

    // Kachel und Komplikation
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.complications.data.source.ktx)
    implementation(libs.androidx.concurrent.futures.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Persistenz
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)

    // Abgleich mit der Handy-App über den Wearable Data Layer (optional: die Uhr läuft auch ohne Handy).
    implementation(project(":shared"))
    implementation(project(":billing"))
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    // Freischaltung: die Play-Store-Seite der App auf dem Handy öffnen.
    implementation(libs.androidx.wear.remote.interactions)

    // Erzeugt das Baseline-Profil auf einer verbundenen Uhr: ./gradlew :app:generateBaselineProfile
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    // Store-Screenshots der Uhr (nur mit -Pscreenshots, siehe oben)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
