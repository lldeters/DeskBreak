package dev.stepcron.mobile.sync

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Letzter Stand der Uhr auf dem Handy: die Rohdaten des Snapshots (wie vom Data Layer, gzip) und wann er ankam,
 * zusammen in einer Datei. Geschrieben wird in eine Hilfsdatei, die dann atomar umbenannt wird – ein Absturz
 * mittendrin hinterlässt den alten oder den neuen Stand, nie einen halben. Reines java.io, daher auf der JVM
 * testbar.
 */
class SnapshotCache(private val directory: File) {

    data class Entry(val bytes: ByteArray, val receivedAtMillis: Long) {
        override fun equals(other: Any?): Boolean =
            other is Entry && receivedAtMillis == other.receivedAtMillis && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + receivedAtMillis.hashCode()
    }

    private val file = File(directory, FILE_NAME)

    /** Gespeicherter Stand oder `null` (keiner, unlesbar oder unbekanntes Format). */
    @Synchronized
    fun read(): Entry? {
        if (!file.isFile) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC) return null
                val receivedAt = input.readLong()
                val length = input.readInt()
                if (length !in 0..MAX_BYTES) return null
                val bytes = ByteArray(length)
                input.readFully(bytes)
                Entry(bytes, receivedAt)
            }
        } catch (e: EOFException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    /** Speichert [bytes] atomar; wirft [IOException], wenn das nicht geht (z. B. Speicher voll). */
    @Synchronized
    fun write(bytes: ByteArray, receivedAtMillis: Long) {
        require(bytes.size <= MAX_BYTES) { "Snapshot zu groß: ${bytes.size} Bytes" }
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Verzeichnis fehlt: $directory")
        val content = ByteArrayOutputStream(bytes.size + HEADER_BYTES).also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeInt(MAGIC)
                out.writeLong(receivedAtMillis)
                out.writeInt(bytes.size)
                out.write(bytes)
            }
        }.toByteArray()
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

    @Synchronized
    fun clear() {
        file.delete()
    }

    companion object {
        const val FILE_NAME = "watch-snapshot.bin"

        /** „DBS1": Kennung und Version des Dateiformats. */
        private const val MAGIC = 0x44425331
        private const val HEADER_BYTES = 16

        /** Obergrenze gegen kaputte Längenangaben (400 Tage Historie sind gzip-komprimiert weit darunter). */
        const val MAX_BYTES = 32 * 1024 * 1024
    }
}
