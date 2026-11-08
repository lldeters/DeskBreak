package dev.stepcron.wear.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.HourSlot
import dev.stepcron.wear.domain.SlotStatus
import dev.stepcron.wear.ui.theme.DeskBreakColors

/**
 * Tages-Timeline: ein Slot pro aktiver Stunde, eingefärbt nach Ergebnis
 * (grün = Bewegung, gelb = Snooze, rot = verpasst, grau = nichts / Zukunft).
 * Die aktuelle Stunde ist umrandet, mehrere Pausen in einer Stunde erscheinen als Punkte.
 */
@Composable
fun DayTimelineChart(
    slots: List<HourSlot>,
    startHour: Int,
    modifier: Modifier = Modifier,
    textMeasurer: TextMeasurer = rememberTextMeasurer(),
) {
    val labelStyle = TextStyle(color = MaterialTheme.colors.onSurfaceVariant, fontSize = 9.sp)
    // Stunden-Beschriftung an den Slot-Grenzen (z. B. 8 · 11 · 14 · 17) einmal pro Fenster messen –
    // in der Komposition, die der Listen-Prefetch vorziehen kann, statt bei jedem Zeichnen.
    val hourLabels = remember(slots.size, startHour, labelStyle, textMeasurer) {
        timelineLabelIndices(slots.size).map { boundary ->
            boundary to textMeasurer.measure("${startHour + boundary}", labelStyle)
        }
    }
    val outlineColor = MaterialTheme.colors.onSurface
    val markerColor = Color.Black.copy(alpha = 0.5f)
    val description = pluralStringResource(
        R.plurals.cd_day_timeline,
        slots.size,
        slots.count { it.status == SlotStatus.DONE },
        slots.size,
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { contentDescription = description },
    ) {
        if (slots.isEmpty()) return@Canvas
        val count = slots.size
        val gap = (if (count > 12) 1.dp else 2.dp).toPx()
        val labelArea = 13.dp.toPx()
        val barHeight = size.height - labelArea
        val slotWidth = (size.width - gap * (count - 1)) / count
        val corner = CornerRadius(minOf(4.dp.toPx(), slotWidth / 3f))

        slots.forEachIndexed { index, slot ->
            val topLeft = Offset(index * (slotWidth + gap), 0f)
            val slotSize = Size(slotWidth, barHeight)
            drawRoundRect(color = slotColor(slot), topLeft = topLeft, size = slotSize, cornerRadius = corner)

            if (slot.movementCount > 1) {
                val dots = minOf(slot.movementCount, 3)
                val radius = minOf(1.6.dp.toPx(), slotWidth / 8f)
                val spacing = radius * 3f
                val firstX = topLeft.x + slotWidth / 2f - spacing * (dots - 1) / 2f
                repeat(dots) { i ->
                    drawCircle(
                        color = markerColor,
                        radius = radius,
                        center = Offset(firstX + i * spacing, barHeight - radius * 2.5f),
                    )
                }
            }
            if (slot.isCurrent) {
                drawRoundRect(
                    color = outlineColor,
                    topLeft = topLeft,
                    size = slotSize,
                    cornerRadius = corner,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        hourLabels.forEach { (boundary, layout) ->
            val x = if (boundary == count) size.width else boundary * (slotWidth + gap)
            val left = (x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(left, barHeight + 2.dp.toPx()))
        }
    }
}

private fun slotColor(slot: HourSlot): Color = when (slot.status) {
    SlotStatus.DONE -> DeskBreakColors.Done
    SlotStatus.SNOOZED -> DeskBreakColors.Snoozed
    SlotStatus.MISSED -> DeskBreakColors.Missed
    SlotStatus.NONE -> if (slot.isFuture) DeskBreakColors.Future else DeskBreakColors.Empty
}

/** Welche Slot-Grenzen beschriftet werden, damit sich die Zahlen nicht überlappen. */
internal fun timelineLabelIndices(count: Int): List<Int> {
    if (count <= 0) return emptyList()
    val step = when {
        count <= 4 -> 1
        count <= 8 -> 2
        count <= 12 -> 3
        count <= 18 -> 4
        else -> 6
    }
    val indices = (0..count step step).toMutableList()
    if (indices.last() != count) {
        if (count - indices.last() < (step + 1) / 2) {
            indices[indices.lastIndex] = count
        } else {
            indices += count
        }
    }
    return indices
}
