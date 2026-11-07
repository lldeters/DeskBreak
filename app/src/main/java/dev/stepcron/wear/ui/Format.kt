package dev.stepcron.wear.ui

import android.content.Context
import dev.stepcron.wear.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

/** Uhrzeit im 24-h-Format, z. B. „10:45". */
fun formatClock(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(clockFormatter)

/** „1 Pause" / „7 Pausen". */
fun breaksText(context: Context, count: Int): String =
    context.resources.getQuantityString(R.plurals.breaks_count, count, count)
