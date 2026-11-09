package dev.stepcron.mobile.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.stats.DayStat
import dev.stepcron.mobile.stats.StatsCalculator
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.currentLocale
import dev.stepcron.mobile.ui.theme.LocalChartColors
import java.time.DayOfWeek

private val GAP = 4.dp

/** Ab diesem Anteil am Tagesziel steht die Tageszahl hell auf dem kräftigen Grün. */
private const val LIGHT_TEXT_SHARE = 0.6f

/**
 * Monatsansicht wie auf der Uhr: ein Feld je Tag, Wochen als Zeilen (Montag zuerst). Je näher die Pausen am
 * Tagesziel [goal], desto kräftiger grün (voll ab erreichtem Ziel); rot umrandet: nur verpasste Erinnerungen;
 * grau: keine Pausen. Heute ist umrandet, künftige Tage bleiben leer. [description] beschreibt das Ganze für
 * TalkBack (die Tagesliste darunter nennt jeden Tag einzeln).
 */
@Composable
fun CalendarHeatmap(weeks: List<List<DayStat?>>, goal: Int, description: String, modifier: Modifier = Modifier) {
    val colors = LocalChartColors.current
    val locale = currentLocale()
    val textMeasurer = rememberTextMeasurer()
    val dayStyle = MaterialTheme.typography.labelSmall
    val darkText = dayStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val lightText = dayStyle.copy(color = colors.onDone)
    val numbers = remember(weeks, goal, darkText, lightText, textMeasurer) {
        weeks.map { week ->
            week.map { day ->
                day?.let {
                    val light = it.movements > 0 && StatsCalculator.goalShare(it.movements, goal) >= LIGHT_TEXT_SHARE
                    textMeasurer.measure(it.date.dayOfMonth.toString(), if (light) lightText else darkText)
                }
            }
        }
    }
    val rows = weeks.size.coerceAtLeast(1)

    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = Formats.weekdayShort(day, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(DAYS_PER_WEEK.toFloat() / rows),
        ) {
            val gap = GAP.toPx()
            val cell = ((size.width - gap * (DAYS_PER_WEEK - 1)) / DAYS_PER_WEEK).coerceAtMost(
                (size.height - gap * (rows - 1)) / rows,
            )
            val radius = CornerRadius(cell / 5f)
            weeks.forEachIndexed { row, days ->
                days.forEachIndexed { column, day ->
                    if (day == null) return@forEachIndexed
                    val topLeft = Offset(column * (cell + gap), row * (cell + gap))
                    val cellSize = Size(cell, cell)
                    val share = StatsCalculator.goalShare(day.movements, goal)
                    val fill = if (day.movements > 0) colors.done.copy(alpha = 0.3f + 0.7f * share) else colors.empty
                    drawRoundRect(color = fill, topLeft = topLeft, size = cellSize, cornerRadius = radius)
                    if (day.movements == 0 && day.missed > 0) {
                        drawRoundRect(
                            color = colors.missed,
                            topLeft = topLeft,
                            size = cellSize,
                            cornerRadius = radius,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                    if (day.isToday) {
                        val inset = 2.dp.toPx()
                        drawRoundRect(
                            color = colors.today,
                            topLeft = topLeft - Offset(inset / 2, inset / 2),
                            size = Size(cell + inset, cell + inset),
                            cornerRadius = radius,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                    numbers[row][column]?.let { text ->
                        drawText(
                            text,
                            topLeft = topLeft + Offset((cell - text.size.width) / 2f, (cell - text.size.height) / 2f),
                        )
                    }
                }
            }
        }
    }
}

private const val DAYS_PER_WEEK = 7
