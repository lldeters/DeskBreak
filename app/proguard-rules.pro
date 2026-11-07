# Projektspezifische R8-Regeln.
# Room, DataStore und Compose liefern ihre eigenen Consumer-Regeln mit.

# Enum-Namen werden in DataStore (AlarmKind) und Room (MovementEventType) als Text gespeichert.
-keepclassmembers enum dev.stepcron.wear.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
