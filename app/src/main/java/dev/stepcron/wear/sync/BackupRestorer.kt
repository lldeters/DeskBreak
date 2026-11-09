package dev.stepcron.wear.sync

import android.database.SQLException
import androidx.annotation.StringRes
import dev.stepcron.shared.sync.RestoreResult
import dev.stepcron.shared.sync.SyncBackup
import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.wear.R
import dev.stepcron.wear.data.MovementHistory
import java.io.IOException

/**
 * Spielt eine Sicherung vom Handy ([SyncBackup]) ein: ersetzt die gesamte Historie und übernimmt die Einstellungen
 * wie eine vollständige Änderung vom Handy. Ist die Sicherung nicht lesbar, bleibt alles, wie es ist.
 */
class BackupRestorer(
    private val history: MovementHistory,
    private val settingsApplier: SettingsUpdateApplier,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Text der Antwort ans Handy, das ihn ggf. anzeigt (in der App: der übersetzte [RestoreFailure.messageRes]). */
    private val describe: (RestoreFailure) -> String = { it.name },
) {

    /**
     * Stellt die Sicherung [bytes] (JSON, [SyncCodec.encodeBackup]; `null`, wenn sie nicht gelesen werden konnte) unter
     * der Kennung [restoreId] wieder her und liefert die Antwort fürs Handy.
     */
    suspend fun restore(restoreId: String, bytes: ByteArray?): RestoreResult {
        val backup = bytes?.let(SyncCodec::decodeBackup) ?: return failure(restoreId, RestoreFailure.UNREADABLE)
        val events = SyncMapping.toMovementEvents(backup.events, clock())
        return try {
            // Erst die Historie (eine Transaktion), dann die Einstellungen über die Engine.
            history.replaceAll(events)
            settingsApplier.apply(SyncMapping.toSettingsUpdate(backup.settings))
            RestoreResult(restoreId = restoreId, success = true, restoredEvents = events.size)
        } catch (e: IOException) {
            failure(restoreId, RestoreFailure.NOT_SAVED)
        } catch (e: SQLException) {
            failure(restoreId, RestoreFailure.NOT_SAVED)
        }
    }

    /** Antwort fürs Handy, wenn die Wiederherstellung [restoreId] aus [reason] scheiterte. */
    fun failure(restoreId: String, reason: RestoreFailure): RestoreResult =
        RestoreResult(restoreId = restoreId, success = false, restoredEvents = 0, message = describe(reason))
}

/** Warum eine Wiederherstellung scheiterte. */
enum class RestoreFailure(@StringRes val messageRes: Int) {
    /** Nicht lesbar: fehlt, ist beschädigt oder stammt von einer neueren App-Version. Nichts wurde geändert. */
    UNREADABLE(R.string.sync_restore_unreadable),

    /** Konnte auf der Uhr nicht gespeichert werden (Datenbank oder Einstellungen). */
    NOT_SAVED(R.string.sync_restore_not_saved),
}
