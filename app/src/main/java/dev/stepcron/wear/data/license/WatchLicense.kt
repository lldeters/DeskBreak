package dev.stepcron.wear.data.license

import dev.stepcron.shared.license.LicenseInfo
import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.LicenseRules
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicLong

/**
 * Die Lizenz auf der Uhr: übernimmt Testbeginn, Play-Abfragen der Uhr und Meldungen des Handys in den
 * [LicenseRepository] und meldet die Folgen – ein geänderter Stand geht ans Handy ([onRecordChanged]), wird die
 * Lizenz dadurch aktiv bzw. inaktiv, gleicht die Engine ab ([onActiveChanged]).
 *
 * Beim Prüfen für die Engine ([isActive]) und beim Prozessstart ([checkPlayIfDue]) stößt sie außerdem im Hintergrund
 * eine Play-Abfrage an ([requestPlayCheck]), wenn die eigene länger zurückliegt, als der Stand es erlaubt
 * ([needsPlayCheck]): Ein Abo gilt nur [LicenseRules.SUBSCRIPTION_TRUST_MILLIS] ohne neue Bestätigung, ein Einmalkauf
 * kann erstattet werden, und ein offener Kauf muss bestätigt werden, bevor Play ihn nach drei Tagen erstattet – die
 * App wird dafür nicht unbedingt geöffnet.
 */
class WatchLicense(
    private val repository: LicenseRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Die Lizenz ist aktiv bzw. inaktiv geworden: Erinnerungen neu abgleichen. */
    private val onActiveChanged: suspend () -> Unit = {},
    /** Der gespeicherte Stand hat sich geändert, z. B. um ihn dem Handy zu melden. */
    private val onRecordChanged: () -> Unit = {},
    /** Play-Abfrage dieser Uhr im Hintergrund anstoßen (kehrt sofort zurück). */
    private val requestPlayCheck: () -> Unit = {},
) {

    val record: Flow<LicenseRecord> get() = repository.record

    /** Letzte im Hintergrund angestoßene Play-Abfrage (nur in diesem Prozess, gegen Wiederholungen bei Fehlern). */
    private val lastPlayCheckRequest = AtomicLong(0L)

    suspend fun status(nowMillis: Long = clock()): LicenseStatus = repository.status(nowMillis)

    /** Darf die Uhr jetzt erinnern? Für die Engine (LicenseCheck). */
    suspend fun isActive(nowMillis: Long): Boolean {
        val record = repository.current()
        requestPlayCheckIfDue(record, nowMillis)
        return LicenseRules.status(record, nowMillis).isActive
    }

    /**
     * Prozessstart: Play-Abfrage anstoßen, falls fällig. Ohne laufende Erinnerungen (Testphase vorbei, ausgeschaltet)
     * fragt die Engine nicht nach [isActive] – ein offener Kauf bliebe dann womöglich unbestätigt.
     */
    suspend fun checkPlayIfDue(nowMillis: Long = clock()) = requestPlayCheckIfDue(repository.current(), nowMillis)

    /** App-Start: beginnt die Testphase, falls noch nicht geschehen (auch nach dem Update einer älteren Version). */
    suspend fun startTrial(): LicenseRecord = change { record, now -> LicenseRules.withTrialStarted(record, now) }

    /**
     * Ergebnis einer erfolgreichen Play-Abfrage dieser Uhr: [owned] zum Zeitpunkt [checkedAtMillis]; [openPurchase],
     * wenn Play dabei einen offenen Kauf meldete (Zahlung ausstehend oder noch nicht bestätigt).
     */
    suspend fun applyOwnCheck(owned: Collection<Unlock>, checkedAtMillis: Long, openPurchase: Boolean = false): LicenseRecord =
        change { record, _ -> LicenseRules.withOwnCheck(record, owned, checkedAtMillis, openPurchase) }

    /** Meldung [info] des Handys übernehmen (per Message oder DataItem; doppelt angekommen ändert nichts). */
    suspend fun applyPeerInfo(info: LicenseInfo): LicenseRecord =
        change { record, now -> LicenseRules.withPeerInfo(record, info, now) }

    private fun requestPlayCheckIfDue(record: LicenseRecord, nowMillis: Long) {
        if (!needsPlayCheck(record, nowMillis)) return
        val last = lastPlayCheckRequest.get()
        val due = last == 0L || nowMillis - last >= PLAY_CHECK_RETRY_MILLIS || nowMillis < last
        if (due && lastPlayCheckRequest.compareAndSet(last, nowMillis)) requestPlayCheck()
    }

    private suspend fun change(transform: (LicenseRecord, Long) -> LicenseRecord): LicenseRecord {
        val now = clock()
        var before = LicenseRecord()
        val after = repository.update { current ->
            before = current
            transform(current, now)
        }
        if (after != before) onRecordChanged()
        if (LicenseRules.status(before, now).isActive != LicenseRules.status(after, now).isActive) onActiveChanged()
        return after
    }

    companion object {
        /** Ohne eigene Play-Abfrage seit so langer Zeit fragt die Uhr bei einem Abo im Hintergrund nach. */
        const val PLAY_CHECK_AFTER_MILLIS = LicenseRules.DAY_MILLIS

        /** Beim eigenen Einmalkauf reicht seltenes Nachfragen (z. B. ob er erstattet wurde). */
        const val LIFETIME_CHECK_AFTER_MILLIS = 7 * LicenseRules.DAY_MILLIS

        /** Höchstens so oft im Hintergrund nachfragen, falls Play nicht antwortet (z. B. offline). */
        const val PLAY_CHECK_RETRY_MILLIS = 60 * 60_000L

        /**
         * Bei einem offenen Kauf (siehe [LicenseRecord.ownOpenPurchase]) so bald wieder fragen: Er soll innerhalb der
         * drei Tage, nach denen Play unbestätigte Käufe erstattet, mehrmals bestätigt werden können.
         */
        const val OPEN_PURCHASE_CHECK_AFTER_MILLIS = PLAY_CHECK_RETRY_MILLIS

        /**
         * Ob die Uhr bei Play nachfragen soll: Die letzte eigene erfolgreiche Abfrage ist mindestens so alt, wie der
         * Stand es erlaubt (oder liegt in der Zukunft, weil die Uhrzeit zurückgestellt wurde) – bei einem offenen Kauf
         * [OPEN_PURCHASE_CHECK_AFTER_MILLIS], beim eigenen Einmalkauf [LIFETIME_CHECK_AFTER_MILLIS], bei einem Abo
         * (eigene Abfrage oder Meldung des Handys) ohne Einmalkauf [PLAY_CHECK_AFTER_MILLIS]. Den Einmalkauf des Handys
         * prüft das Handy; ganz ohne Freischaltung gibt es nichts zu prüfen.
         */
        fun needsPlayCheck(record: LicenseRecord, nowMillis: Long): Boolean {
            val checkAfter = playCheckAfterMillis(record) ?: return false
            val sinceCheck = nowMillis - record.ownCheckedAtMillis
            return sinceCheck >= checkAfter || sinceCheck < 0L
        }

        private fun playCheckAfterMillis(record: LicenseRecord): Long? {
            val own = Unlock.fromName(record.ownUnlock)
            val peer = Unlock.fromName(record.peerUnlock)
            return when {
                record.ownOpenPurchase -> OPEN_PURCHASE_CHECK_AFTER_MILLIS
                own == Unlock.LIFETIME -> LIFETIME_CHECK_AFTER_MILLIS
                peer == Unlock.LIFETIME -> null
                own == Unlock.SUBSCRIPTION || peer == Unlock.SUBSCRIPTION -> PLAY_CHECK_AFTER_MILLIS
                else -> null
            }
        }
    }
}
