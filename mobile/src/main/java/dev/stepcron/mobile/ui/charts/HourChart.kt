package dev.stepcron.mobile.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.stats.HourlyCounts
import dev.stepcron.mobile.ui.theme.LocalChartColors

/** Mindestens dieser Bereich wird gezeigt, damit wenige Daten nicht wie ein ganzer Tag aussehen. */
private const val DEFAULT_FIRST_HOUR = 8
private const val DEFAULT_LAST_HOUR = 17

/**
 * Pausen (grün) und verpasste Erinnerungen (rot-orange, darüber) je Stunde des Tages – über alle Tage summiert.
 * Zeigt den Bereich mit Daten (mindestens 8–18 Uhr), beschriftet jede zweite Stunde. [description] ist die
 * TalkBack-Beschreibung des Ganzen.
 */
@Composable
fun HourChart(hourly: List<HourlyCounts>, highlightHour: Int?, description: String, modifier: Modifier = Modifier) {
    val colors = LocalChartColors.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.label)
    val first = minOf(DEFAULT_FIRST_HOUR, hourly.minOfOrNull { it.hour } ?: DEFAULT_FIRST_HOUR)
    val last = maxOf(DEFAULT_LAST_HOUR, hourly.maxOfOrNull { it.hour } ?: DEFAULT_LAST_HOUR)
    val hours = (first..last).toList()
    val byHour = remember(hourly) { hourly.associateBy { it.hour } }
    val labels = remember(hours, labelStyle, textMeasurer) {
        hours.associateWith { hour -> if (hour % 2 == 0) textMeasurer.measure(hour.toString(), labelStyle) else null }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val labelHeight = labels.values.filterNotNull().maxOfOrNull { it.size.height }?.toFloat() ?: 0f
        val gap = 4.dp.toPx()
        val bottom = size.height - labelHeight - gap
        val maxValue = (hourly.maxOfOrNull { it.movements + it.missed } ?: 0).coerceAtLeast(1).toFloat()
        val slot = size.width / hours.size
        val barWidth = (slot * 0.62f).coerceAtMost(18.dp.toPx())
        val radius = CornerRadius(3.dp.toPx())
        // Grundlinie
        drawLine(colors.guide.copy(alpha = 0.4f), Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())
        hours.forEachIndexed { index, hour ->
            val counts = byHour[hour]
            val left = slot * index + (slot - barWidth) / 2f
            if (counts != null) {
                val doneHeight = bottom * counts.movements / maxValue
                val missedHeight = bottom * counts.missed / maxValue
                val doneColor = if (hour == highlightHour) colors.done else colors.doneSoft
                if (doneHeight > 0f) drawRoundRect(doneColor, Offset(left, bottom - doneHeight), Size(barWidth, doneHeight), radius)
                if (missedHeight > 0f) {
                    val top = bottom - doneHeight - (if (doneHeight > 0f) 2.dp.toPx() else 0f) - missedHeight
                    drawRoundRect(colors.missed, Offset(left, top), Size(barWidth, missedHeight), radius)
                }
            }
            labels[hour]?.let { label ->
                drawText(label, topLeft = Offset(slot * index + (slot - label.size.width) / 2f, bottom + gap))
            }
        }
    }
}
