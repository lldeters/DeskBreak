package dev.stepcron.wear.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.DayStat
import dev.stepcron.wear.ui.theme.DeskBreakColors

private val CELL = 15.dp
private val GAP = 3.dp

/**
 * Monatsansicht: ein Feld je Tag, Wochen als Zeilen (Montag zuerst). Grün, je näher die Pausen des Tages am
 * Tagesziel [goal] liegen (voll ab erreichtem Ziel); rot umrandet: nur verpasste Erinnerungen; grau: keine Daten.
 * Heute ist umrandet, künftige Tage fehlen. [description] beschreibt das Ganze für TalkBack.
 */
@Composable
fun MonthHeatmap(
    weeks: List<List<DayStat?>>,
    goal: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    val weekdayLabels = stringArrayResource(R.array.weekday_short)
    val outline = MaterialTheme.colors.primary
    val gridWidth = CELL * 7 + GAP * 6
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label.take(2),
                    modifier = Modifier.width(CELL),
                    fontSize = 8.sp,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Canvas(modifier = Modifier.size(width = gridWidth, height = CELL * weeks.size + GAP * (weeks.size - 1).coerceAtLeast(0))) {
            val cell = CELL.toPx()
            val gap = GAP.toPx()
            val radius = CornerRadius(cell / 4f)
            weeks.forEachIndexed { row, days ->
                days.forEachIndexed { column, day ->
                    if (day == null) return@forEachIndexed
                    val topLeft = Offset(column * (cell + gap), row * (cell + gap))
                    val size = Size(cell, cell)
                    val share = (day.movements.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val fill = when {
                        day.movements > 0 -> DeskBreakColors.Done.copy(alpha = 0.3f + 0.7f * share)
                        else -> DeskBreakColors.Empty
                    }
                    drawRoundRect(color = fill, topLeft = topLeft, size = size, cornerRadius = radius)
                    if (day.movements == 0 && day.missed > 0) {
                        drawRoundRect(
                            color = DeskBreakColors.Missed,
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = radius,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                    if (day.isToday) {
                        val inset = 1.dp.toPx()
                        drawRoundRect(
                            color = outline,
                            topLeft = topLeft - Offset(inset, inset),
                            size = Size(cell + 2 * inset, cell + 2 * inset),
                            cornerRadius = radius,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}
