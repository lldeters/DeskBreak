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
 * Beim Prüfen für die Engine ([isActive]) stößt sie außerdem im Hintergrund eine Play-Abfrage an
 * ([requestPlayCheck]), wenn ein Abo im Spiel ist und die Uhr es länger nicht selbst geprüft hat ([needsPlayCheck]):
 * Ein Abo gilt nur [LicenseRules.SUBSCRIPTION_TRUST_MILLIS] ohne neue Bestätigung, und die App wird nicht unbedingt
 * geöffnet.
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
        if (needsPlayCheck(record, nowMillis)) {
            val last = lastPlayCheckRequest.get()
            val due = last == 0L || nowMillis - last >= PLAY_CHECK_RETRY_MILLIS || nowMillis < last
            if (due && lastPlayCheckRequest.compareAndSet(last, nowMillis)) requestPlayCheck()
        }
        return LicenseRules.status(record, nowMillis).isActive
    }

    /** App-Start: beginnt die Testphase, falls noch nicht geschehen (auch nach dem Update einer älteren Version). */
    suspend fun startTrial(): LicenseRecord = change { record, now -> LicenseRules.withTrialStarted(record, now) }

    /** Ergebnis einer erfolgreichen Play-Abfrage dieser Uhr: [owned] zum Zeitpunkt [checkedAtMillis]. */
    suspend fun applyOwnCheck(owned: Collection<Unlock>, checkedAtMillis: Long): LicenseRecord =
        change { record, _ -> LicenseRules.withOwnCheck(record, owned, checkedAtMillis) }

    /** Meldung [info] des Handys übernehmen. */
    suspend fun applyPeerInfo(info: LicenseInfo): LicenseRecord =
        change { record, _ -> LicenseRules.withPeerInfo(record, info) }

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

        /** Höchstens so oft im Hintergrund nachfragen, falls Play nicht antwortet (z. B. offline). */
        const val PLAY_CHECK_RETRY_MILLIS = 60 * 60_000L

        /**
         * Ob die Uhr bei Play nachfragen soll: Es gibt ein Abo (eigene Abfrage oder Meldung des Handys), keinen
         * Einmalkauf, und die letzte eigene erfolgreiche Abfrage ist mindestens [PLAY_CHECK_AFTER_MILLIS] alt (oder liegt
         * in der Zukunft, weil die Uhrzeit zurückgestellt wurde).
         */
        fun needsPlayCheck(record: LicenseRecord, nowMillis: Long): Boolean {
            val unlocks = setOfNotNull(Unlock.fromName(record.ownUnlock), Unlock.fromName(record.peerUnlock))
            if (Unlock.SUBSCRIPTION !in unlocks || Unlock.LIFETIME in unlocks) return false
            val sinceCheck = nowMillis - record.ownCheckedAtMillis
            return sinceCheck >= PLAY_CHECK_AFTER_MILLIS || sinceCheck < 0L
        }
    }
}
