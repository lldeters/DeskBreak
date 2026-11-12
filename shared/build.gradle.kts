// Gemeinsam für Uhr (:app) und Handy (:mobile): Pfade und Datenformat des Abgleichs über den
// Wearable Data Layer. Ohne Android-Abhängigkeiten im Code, damit es auf der JVM testbar bleibt.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.stepcron.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
