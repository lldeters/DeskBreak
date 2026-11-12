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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.stats.DayStat
import dev.stepcron.mobile.stats.StatsCalculator
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.currentLocale
import dev.stepcron.mobile.ui.theme.ChartColors
import dev.stepcron.mobile.ui.theme.LocalChartColors
import java.time.DayOfWeek

private val GAP = 6.dp

/**
 * Monatsansicht: ein Feld je Tag, Wochen als Zeilen (Montag zuerst). Je näher die Pausen am Tagesziel [goal], desto
 * kräftiger grün (voll ab erreichtem Ziel); grau: Erinnerungstag ohne Pause; gestrichelt: freier Tag laut
 * Wochenplan; rot umrandet: nur verpasste Erinnerungen. Heute ist umrandet, künftige Tage bleiben leer.
 * [description] beschreibt das Ganze für TalkBack (die Tagesliste darunter nennt jeden Tag einzeln).
 */
@Composable
fun CalendarHeatmap(weeks: List<List<DayStat?>>, goal: Int, description: String, modifier: Modifier = Modifier) {
    val colors = LocalChartColors.current
    val locale = currentLocale()
    val textMeasurer = rememberTextMeasurer()
    val dayStyle = MaterialTheme.typography.labelMedium
    val darkText = dayStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val lightText = dayStyle.copy(color = colors.onDone, fontWeight = FontWeight.SemiBold)
    val dimText = dayStyle.copy(color = colors.label.copy(alpha = 0.7f))
    val numbers = remember(weeks, goal, darkText, lightText, dimText, textMeasurer) {
        weeks.map { week ->
            week.map { day ->
                day?.let {
                    val style = when {
                        it.movements >= goal -> lightText
                        !it.scheduled && !it.hasData -> dimText
                        else -> darkText
                    }
                    textMeasurer.measure(it.date.dayOfMonth.toString(), style)
                }
            }
        }
    }
    val rows = weeks.size.coerceAtLeast(1)

    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = Formats.weekdayShort(day, locale).take(2),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(DAYS_PER_WEEK.toFloat() / rows),
        ) {
            val gap = GAP.toPx()
            val cell = ((size.width - gap * (DAYS_PER_WEEK - 1)) / DAYS_PER_WEEK).coerceAtMost(
                (size.height - gap * (rows - 1)) / rows,
            )
            val radius = CornerRadius(cell / 4f)
            val stroke = 2.dp.toPx()
            weeks.forEachIndexed { row, days ->
                days.forEachIndexed { column, day ->
                    if (day == null) return@forEachIndexed
                    val topLeft = Offset(column * (cell + gap), row * (cell + gap))
                    val cellSize = Size(cell, cell)
                    if (!day.scheduled && !day.hasData) {
                        // Freier Tag: nur gestrichelter Umriss – kein „schlechter" Tag.
                        drawRoundRect(
                            color = colors.guide,
                            topLeft = topLeft + Offset(stroke / 2, stroke / 2),
                            size = Size(cell - stroke, cell - stroke),
                            cornerRadius = radius,
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
                        )
                    } else {
                        drawRoundRect(color = fillFor(day, goal, colors), topLeft = topLeft, size = cellSize, cornerRadius = radius)
                    }
                    if (day.movements == 0 && day.missed > 0) {
                        drawRoundRect(
                            color = colors.missed,
                            topLeft = topLeft + Offset(stroke / 2, stroke / 2),
                            size = Size(cell - stroke, cell - stroke),
                            cornerRadius = radius,
                            style = Stroke(width = stroke),
                        )
                    }
                    if (day.isToday) {
                        drawRoundRect(
                            color = colors.today,
                            topLeft = topLeft - Offset(stroke, stroke),
                            size = Size(cell + 2 * stroke, cell + 2 * stroke),
                            cornerRadius = CornerRadius(radius.x + stroke),
                            style = Stroke(width = stroke),
                        )
                    }
                    numbers[row][column]?.let { text ->
                        drawText(text, topLeft = topLeft + Offset((cell - text.size.width) / 2f, (cell - text.size.height) / 2f))
                    }
                }
            }
        }
    }
}

/** Grau ohne Pause, dann von zartem zu kräftigem Grün je nach Anteil am Ziel, voll ab erreichtem Ziel. */
private fun fillFor(day: DayStat, goal: Int, colors: ChartColors): Color = when {
    day.movements == 0 -> colors.empty
    day.movements >= goal -> colors.done
    else -> lerp(colors.doneSoft.copy(alpha = 0.55f), colors.doneSoft, StatsCalculator.goalShare(day.movements, goal))
}

private const val DAYS_PER_WEEK = 7
