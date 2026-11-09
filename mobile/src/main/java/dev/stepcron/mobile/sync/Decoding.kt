package dev.stepcron.mobile.sync

import dev.stepcron.shared.sync.SyncBackup
import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.shared.sync.SyncSnapshot
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/** Ergebnis beim Lesen von Daten der Uhr bzw. einer Sicherungsdatei. */
sealed interface Decoded<out T> {
    data class Ok<T>(val value: T) : Decoded<T>

    /** Neueres Datenformat als diese App kennt. */
    data class TooNew(val version: Int) : Decoded<Nothing>

    data object Invalid : Decoded<Nothing>
}

/**
 * Liest Snapshot und Sicherung über [SyncCodec]. Liefert SyncCodec `null`, wird nur noch die Versionsnummer
 * gelesen: So unterscheidet die App „kaputt" von „neueres Format – bitte App aktualisieren" (SyncCodec selbst
 * meldet beides als `null`).
 */
object SyncDecoding {

    private val json = Json { ignoreUnknownKeys = true }

    fun snapshot(bytes: ByteArray): Decoded<SyncSnapshot> {
        val snapshot = SyncCodec.decodeSnapshot(bytes)
        if (snapshot != null) {
            // Ohne sieben Tage im Wochenplan kann der Stand nicht von einer intakten Uhr stammen.
            return if (snapshot.settings.schedule.size == DAYS_PER_WEEK) Decoded.Ok(snapshot) else Decoded.Invalid
        }
        return tooNewOrInvalid { gunzip(bytes) }
    }

    fun backup(bytes: ByteArray): Decoded<SyncBackup> {
        val backup = SyncCodec.decodeBackup(bytes)
        if (backup != null) return Decoded.Ok(backup)
        return tooNewOrInvalid { bytes.toString(Charsets.UTF_8) }
    }

    private fun tooNewOrInvalid(text: () -> String): Decoded<Nothing> {
        val version = try {
            json.parseToJsonElement(text()).jsonObject["version"]?.jsonPrimitive?.intOrNull
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: IOException) {
            null
        }
        return if (version != null && version > SyncCodec.VERSION) Decoded.TooNew(version) else Decoded.Invalid
    }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }

    private const val DAYS_PER_WEEK = 7
}
