package dev.stepcron.mobile.stats

import dev.stepcron.shared.sync.SyncBackup
import dev.stepcron.shared.sync.SyncEvent
import dev.stepcron.shared.sync.SyncSnapshot
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV-Export der Historie: Kopfzeile `date,time,type,interval_minutes`, eine Zeile je Ereignis, ältestes zuerst,
 * Datum (ISO) und Uhrzeit (HH:mm) in der Zeitzone der Uhr. Zeilenende CRLF nach RFC 4180.
 */
object CsvExport {
    const val HEADER = "date,time,type,interval_minutes"
    private const val LINE_END = "\r\n"
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun build(snapshot: SyncSnapshot): String = build(snapshot.events, StatsCalculator.zoneOf(snapshot))

    fun build(events: List<SyncEvent>, zone: ZoneId): String {
        val out = StringBuilder(HEADER.length + events.size * AVERAGE_LINE_LENGTH)
        out.append(HEADER).append(LINE_END)
        // sortedBy ist stabil: gleichzeitige Ereignisse behalten die Reihenfolge der Uhr.
        for (event in events.sortedBy { it.timestampMillis }) {
            val time = Instant.ofEpochMilli(event.timestampMillis).atZone(zone)
            out.append(DateTimeFormatter.ISO_LOCAL_DATE.format(time)).append(',')
                .append(timeFormatter.format(time)).append(',')
                .append(escape(event.type)).append(',')
                .append(event.intervalMinutes).append(LINE_END)
        }
        return out.toString()
    }

    /** Feld nach RFC 4180: in Anführungszeichen, sobald Komma, Anführungszeichen oder Zeilenumbruch vorkommen. */
    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    fun fileName(today: LocalDate): String = "deskbreak-$today.csv"

    private const val AVERAGE_LINE_LENGTH = 40
}

/** Was eine Sicherung enthält – für die Rückfrage vor der Wiederherstellung. */
data class BackupSummary(
    val eventCount: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val createdAtMillis: Long,
    val zone: ZoneId,
)

object BackupFiles {

    /** Sicherung aus dem letzten Stand der Uhr (Einstellungen und die übertragene Historie). */
    fun fromSnapshot(snapshot: SyncSnapshot, nowMillis: Long): SyncBackup = SyncBackup(
        createdAtMillis = nowMillis,
        zoneId = snapshot.zoneId,
        settings = snapshot.settings,
        events = snapshot.events,
    )

    fun summarize(backup: SyncBackup): BackupSummary {
        val zone = try {
            ZoneId.of(backup.zoneId)
        } catch (e: DateTimeException) {
            ZoneId.systemDefault()
        }
        val first = backup.events.minOfOrNull { it.timestampMillis }
        val last = backup.events.maxOfOrNull { it.timestampMillis }
        return BackupSummary(
            eventCount = backup.events.size,
            firstDate = first?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
            lastDate = last?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
            createdAtMillis = backup.createdAtMillis,
            zone = zone,
        )
    }

    fun fileName(today: LocalDate): String = "deskbreak-backup-$today.json"

    /** Größte Datei, die als Sicherung gelesen wird (400 Tage Historie sind weit darunter). */
    const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
}
