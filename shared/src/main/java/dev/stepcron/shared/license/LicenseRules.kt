package dev.stepcron.shared.license

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Regeln der Lizenz – reine Funktionen auf [LicenseRecord], auf beiden Geräten gleich. */
object LicenseRules {

    const val TRIAL_DAYS = 3

    const val DAY_MILLIS = 24L * 60 * 60 * 1000

    const val TRIAL_MILLIS = TRIAL_DAYS * DAY_MILLIS

    /**
     * So lange gilt ein Abo ohne neue Bestätigung durch Play: ein Abrechnungsmonat plus Kulanz. Danach muss eines
     * der Geräte es neu bestätigen (Play-Abfrage beim Öffnen der App bzw. im Hintergrund).
     */
    const val SUBSCRIPTION_TRUST_MILLIS = 35 * DAY_MILLIS

    /**
     * So weit darf ein Zeitpunkt einer Play-Abfrage in der Zukunft liegen und trotzdem gelten (etwas abweichende
     * Uhrzeit der Geräte). Was weiter vorausliegt, stammt von einer falsch gestellten Uhr und darf weder ein Abo auf
     * Jahre bestätigen noch spätere, richtige Meldungen blockieren.
     */
    const val CLOCK_TOLERANCE_MILLIS = DAY_MILLIS

    /** Stand der Lizenz zum Zeitpunkt [nowMillis]. Eine noch nicht begonnene Testphase zählt als volle Testphase. */
    fun status(record: LicenseRecord, nowMillis: Long): LicenseStatus {
        effectiveUnlock(record, nowMillis)?.let { return LicenseStatus.Unlocked(it) }
        val start = if (record.trialStartMillis > 0L) minOf(record.trialStartMillis, nowMillis) else nowMillis
        val end = start + TRIAL_MILLIS
        if (nowMillis >= end) return LicenseStatus.Expired(end)
        val left = end - nowMillis
        val daysLeft = ((left + DAY_MILLIS - 1) / DAY_MILLIS).toInt().coerceIn(1, TRIAL_DAYS)
        return LicenseStatus.Trial(endsAtMillis = end, daysLeft = daysLeft)
    }

    /** Gültige Freischaltung aus eigener Abfrage oder Meldung des anderen Geräts; Einmalkauf vor Abo. */
    fun effectiveUnlock(record: LicenseRecord, nowMillis: Long): Unlock? = Unlock.best(
        listOfNotNull(
            trusted(Unlock.fromName(record.ownUnlock), record.ownCheckedAtMillis, nowMillis),
            trusted(Unlock.fromName(record.peerUnlock), record.peerCheckedAtMillis, nowMillis),
        ),
    )

    /**
     * Beginnt die Testphase, falls noch nicht geschehen. Liegt der gespeicherte Beginn in der Zukunft (Uhrzeit
     * zurückgestellt), gilt ab jetzt – so kann eine falsch gehende Uhr die Testphase nicht verlängern.
     */
    fun withTrialStarted(record: LicenseRecord, nowMillis: Long): LicenseRecord = when {
        record.trialStartMillis <= 0L || record.trialStartMillis > nowMillis -> record.copy(trialStartMillis = nowMillis)
        else -> record
    }

    /**
     * Ergebnis einer erfolgreichen Play-Abfrage auf diesem Gerät: [owned] sind die gefundenen, bezahlten Käufe
     * (leer = nichts gekauft bzw. Abo abgelaufen); [openPurchase], wenn Play dabei einen offenen Kauf meldete (Zahlung
     * ausstehend oder noch nicht bestätigt, siehe [LicenseRecord.ownOpenPurchase]). Eine fehlgeschlagene Abfrage
     * ändert nichts – dafür diese Funktion nicht aufrufen.
     */
    fun withOwnCheck(
        record: LicenseRecord,
        owned: Collection<Unlock>,
        nowMillis: Long,
        openPurchase: Boolean = false,
    ): LicenseRecord = record.copy(
        ownUnlock = Unlock.best(owned)?.name,
        ownCheckedAtMillis = nowMillis,
        ownOpenPurchase = openPurchase,
    )

    /**
     * Meldung [info] des anderen Geräts übernehmen: frühester Testbeginn beider Geräte; dessen Freischaltung,
     * sofern die Meldung nicht älter ist als die zuletzt übernommene (Nachrichten können sich überholen). Liegt die
     * zuletzt übernommene mehr als [CLOCK_TOLERANCE_MILLIS] nach [nowMillis] (Uhr des anderen Geräts ging vor), gilt
     * jede neue Meldung – sonst hielte die falsche Zeit den alten Stand fest, etwa ein längst gekündigtes Abo.
     */
    fun withPeerInfo(record: LicenseRecord, info: LicenseInfo, nowMillis: Long): LicenseRecord {
        val trialStart = listOf(record.trialStartMillis, info.trialStartMillis).filter { it > 0L }.minOrNull() ?: 0L
        val storedFromTheFuture = record.peerCheckedAtMillis > nowMillis + CLOCK_TOLERANCE_MILLIS
        val newer = info.checkedAtMillis >= record.peerCheckedAtMillis || storedFromTheFuture
        return record.copy(
            trialStartMillis = trialStart,
            peerUnlock = if (newer) Unlock.fromName(info.unlock)?.name else record.peerUnlock,
            peerCheckedAtMillis = if (newer) info.checkedAtMillis else record.peerCheckedAtMillis,
        )
    }

    /** Was dieses Gerät dem anderen meldet: Testbeginn und die eigene Play-Abfrage (nicht die weitergereichte). */
    fun infoFor(record: LicenseRecord): LicenseInfo = LicenseInfo(
        trialStartMillis = record.trialStartMillis,
        unlock = record.ownUnlock,
        checkedAtMillis = record.ownCheckedAtMillis,
    )

    private fun trusted(unlock: Unlock?, checkedAtMillis: Long, nowMillis: Long): Unlock? = when (unlock) {
        null -> null
        Unlock.LIFETIME -> unlock
        // Etwas in der Zukunft (Uhrzeit leicht verstellt) gilt noch, weit voraus nicht: Sonst bestätigte eine falsch
        // gestellte Uhr ein Abo auf Jahre. Die Geräte fragen dann bald selbst neu (Abfrage aus der „Zukunft" gilt als alt).
        Unlock.SUBSCRIPTION -> unlock.takeIf {
            checkedAtMillis > 0L &&
                checkedAtMillis <= nowMillis + CLOCK_TOLERANCE_MILLIS &&
                nowMillis - checkedAtMillis <= SUBSCRIPTION_TRUST_MILLIS
        }
    }
}

/** Speichern und Übertragen von [LicenseRecord] und [LicenseInfo] als JSON; Lesen liefert bei Fehlern `null`. */
object LicenseCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeRecord(record: LicenseRecord): String = json.encodeToString(LicenseRecord.serializer(), record)

    fun decodeRecord(text: String): LicenseRecord? = decode { json.decodeFromString(LicenseRecord.serializer(), text) }

    fun encodeInfo(info: LicenseInfo): ByteArray =
        json.encodeToString(LicenseInfo.serializer(), info).toByteArray(Charsets.UTF_8)

    fun decodeInfo(bytes: ByteArray): LicenseInfo? =
        decode { json.decodeFromString(LicenseInfo.serializer(), bytes.toString(Charsets.UTF_8)) }

    private inline fun <T> decode(block: () -> T): T? = try {
        block()
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
