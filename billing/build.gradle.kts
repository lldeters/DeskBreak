// Google Play Billing für Uhr (:app) und Handy (:mobile): Angebote und Käufe der Freischaltung
// (siehe dev.stepcron.shared.license). Die Oberfläche kennt nur [BillingGateway].
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.stepcron.billing"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":shared"))
    api(libs.kotlinx.coroutines.android)
    implementation(libs.play.billing.ktx)
    testImplementation(libs.junit)
}
