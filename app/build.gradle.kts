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
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
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

    // Erzeugt das Baseline-Profil auf einer verbundenen Uhr: ./gradlew :app:generateBaselineProfile
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
}
