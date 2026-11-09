// Erzeugt das Baseline-Profil der App auf einer verbundenen Uhr: ./gradlew :app:generateBaselineProfile
// (siehe BaselineProfileGenerator). Gehört nicht zur ausgelieferten App.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "dev.stepcron.wear.baselineprofile"
    compileSdk = 34

    defaultConfig {
        minSdk = 30
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

baselineProfile {
    // Auf der per ADB verbundenen Uhr erzeugen statt auf einem von Gradle verwalteten Emulator.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
