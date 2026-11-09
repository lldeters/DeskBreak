package dev.stepcron.shared.sync

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Ver- und Entschlüsseln der Sync-Daten. Lesen liefert bei kaputten oder unbekannten Daten `null` statt einer
 * Ausnahme – Daten kommen von einem anderen Gerät und ggf. einer anderen App-Version. Unbekannte Felder
 * (neuere Version) werden übergangen.
 */
object SyncCodec {

    /** Version des Datenformats. Höher heißt: inkompatibel geändert (neue Felder allein erhöhen sie nicht). */
    const val VERSION = 1

    /** So viele Tage Historie enthält ein [SyncSnapshot]. */
    const val HISTORY_DAYS = 400

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prettyJson = Json(json) { prettyPrint = true }

    fun encodeSnapshot(snapshot: SyncSnapshot): ByteArray = gzip(json.encodeToString(SyncSnapshot.serializer(), snapshot))

    fun decodeSnapshot(bytes: ByteArray): SyncSnapshot? =
        decode { json.decodeFromString(SyncSnapshot.serializer(), gunzip(bytes)) }?.takeIf { it.version <= VERSION }

    fun encodeSettingsUpdate(update: SettingsUpdate): ByteArray =
        json.encodeToString(SettingsUpdate.serializer(), update).toByteArray(Charsets.UTF_8)

    fun decodeSettingsUpdate(bytes: ByteArray): SettingsUpdate? =
        decode { json.decodeFromString(SettingsUpdate.serializer(), bytes.toString(Charsets.UTF_8)) }

    /** Sicherung als lesbares JSON (so steht sie auch in der Datei). */
    fun encodeBackup(backup: SyncBackup): ByteArray =
        prettyJson.encodeToString(SyncBackup.serializer(), backup).toByteArray(Charsets.UTF_8)

    fun decodeBackup(bytes: ByteArray): SyncBackup? =
        decode { json.decodeFromString(SyncBackup.serializer(), bytes.toString(Charsets.UTF_8)) }
            ?.takeIf { it.version <= VERSION && it.settings.schedule.size == DAYS_PER_WEEK }

    fun encodeRestoreResult(result: RestoreResult): ByteArray =
        json.encodeToString(RestoreResult.serializer(), result).toByteArray(Charsets.UTF_8)

    fun decodeRestoreResult(bytes: ByteArray): RestoreResult? =
        decode { json.decodeFromString(RestoreResult.serializer(), bytes.toString(Charsets.UTF_8)) }

    private const val DAYS_PER_WEEK = 7

    private inline fun <T> decode(block: () -> T): T? = try {
        block()
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IOException) {
        null
    }

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
}
