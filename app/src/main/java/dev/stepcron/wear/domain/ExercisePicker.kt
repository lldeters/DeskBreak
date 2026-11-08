package dev.stepcron.wear.domain

/** Wählt die Bewegungsidee einer Erinnerung: gut verteilt, aber ohne Zufall (wiederholbar und testbar). */
object ExercisePicker {

    /** Index in eine Liste mit [count] Übungen für die Erinnerung um [reminderAtMillis]. */
    fun indexFor(reminderAtMillis: Long, count: Int): Int {
        require(count > 0) { "Es braucht mindestens eine Übung" }
        val minute = Math.floorDiv(reminderAtMillis, 60_000L)
        return Math.floorMod(mix(minute), count.toLong()).toInt()
    }

    /**
     * SplitMix64-Finalisierer: Schon benachbarte Minuten ergeben völlig verschiedene Werte. Ein schlichtes
     * `minute % count` würde bei festem Intervall (z. B. alle 45 min) nur wenige Übungen treffen.
     */
    private fun mix(value: Long): Long {
        var z = value + -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }
}
