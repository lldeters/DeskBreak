# kotlinx.serialization bringt eigene Regeln mit; die Modelle hier werden nur über ihre generierten
# Serializer angesprochen. Für alle Fälle die Serializer der Sync-Modelle behalten.
-keepclassmembers class dev.stepcron.shared.sync.** {
    *** Companion;
}
-keepclasseswithmembers class dev.stepcron.shared.sync.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class dev.stepcron.shared.license.** {
    *** Companion;
}
-keepclasseswithmembers class dev.stepcron.shared.license.** {
    kotlinx.serialization.KSerializer serializer(...);
}
