package dev.stepcron.mobile.license

import dev.stepcron.shared.license.LicenseCodec
import dev.stepcron.shared.license.LicenseRecord
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Lizenz-Stand des Handys ([LicenseRecord]) als kleine JSON-Datei ([LicenseCodec]). Geschrieben wird – wie beim
 * SnapshotCache – in eine Hilfsdatei, die dann atomar umbenannt wird: Ein Absturz mittendrin hinterlässt den alten
 * oder den neuen Stand, nie einen halben. Reines java.io, daher auf der JVM testbar.
 */
class LicenseStore(private val directory: File) {

    private val file = File(directory, FILE_NAME)

    /** Gespeicherter Stand oder `null` (keiner, unlesbar oder unbekanntes Format). */
    @Synchronized
    fun read(): LicenseRecord? {
        if (!file.isFile || file.length() > MAX_BYTES) return null
        return try {
            LicenseCodec.decodeRecord(file.readText(Charsets.UTF_8))
        } catch (e: IOException) {
            null
        }
    }

    /** Speichert [record] atomar; wirft [IOException], wenn das nicht geht (z. B. Speicher voll). */
    @Synchronized
    fun write(record: LicenseRecord) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Verzeichnis fehlt: $directory")
        val content = LicenseCodec.encodeRecord(record).toByteArray(Charsets.UTF_8)
        val temp = File(directory, "$FILE_NAME.tmp")
        FileOutputStream(temp).use { out ->
            out.write(content)
            // Erst auf den Datenträger, dann umbenennen: sonst kann nach einem Stromausfall eine leere Datei stehen.
            out.fd.sync()
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val FILE_NAME = "license.json"

        /** Obergrenze gegen eine kaputte Datei; ein Stand hat gut 150 Bytes. */
        private const val MAX_BYTES = 64 * 1024L
    }
}
