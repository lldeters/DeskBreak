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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.stats.DayStat
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.breaksText
import dev.stepcron.mobile.ui.currentLocale
import dev.stepcron.mobile.ui.theme.LocalChartColors

/**
 * Die letzten sieben Tage als Säulen: die Pausen grün (kräftig ab erreichtem Tagesziel), verpasste Erinnerungen
 * mit kleinem Abstand darüber in Rot-Orange. Über jeder Säule die Zahl der Pausen, darunter der Wochentag (heute
 * hervorgehoben); freie Tage ohne Daten zeigen „frei" statt einer leeren Säule. Gestrichelt das Tagesziel.
 * TalkBack liest alle Tage als eine Beschreibung vor.
 */
@Composable
fun WeekBarChart(days: List<DayStat>, goal: Int, modifier: Modifier = Modifier) {
    val colors = LocalChartColors.current
    val locale = currentLocale()
    val textMeasurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val valueStyle = typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val freeStyle = typography.labelSmall.copy(color = colors.label)
    val weekdayStyle = typography.labelMedium.copy(color = colors.label)
    val todayStyle = weekdayStyle.copy(color = colors.today, fontWeight = FontWeight.Bold)
    val freeText = stringResource(R.string.chart_free)

    val dayDescriptions = ArrayList<String>(days.size)
    for (day in days) {
        dayDescriptions += if (!day.scheduled && !day.hasData) {
            stringResource(R.string.cd_week_day_free, Formats.shortDate(day.date, locale))
        } else {
            stringResource(R.string.cd_week_day, Formats.shortDate(day.date, locale), breaksText(day.movements), day.missed)
        }
    }
    val description = stringResource(R.string.cd_week_chart, goal, dayDescriptions.joinToString("; "))

    // Beschriftungen einmal je Datenstand messen statt bei jedem Zeichnen.
    val labels = remember(days, locale, valueStyle, weekdayStyle, todayStyle, freeStyle, freeText, textMeasurer) {
        days.map { day ->
            val free = !day.scheduled && !day.hasData
            Triple(
                textMeasurer.measure(if (free) freeText else day.movements.toString(), if (free) freeStyle else valueStyle),
                textMeasurer.measure(Formats.weekdayShort(day.date.dayOfWeek, locale), if (day.isToday) todayStyle else weekdayStyle),
                free,
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(176.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (days.isEmpty()) return@Canvas
        val gap = 6.dp.toPx()
        val valueHeight = labels.maxOf { it.first.size.height }.toFloat()
        val weekdayHeight = labels.maxOf { it.second.size.height }.toFloat()
        val top = valueHeight + gap
        val bottom = size.height - weekdayHeight - gap
        val chartHeight = (bottom - top).coerceAtLeast(1f)
        val maxValue = maxOf(goal, days.maxOf { it.movements + it.missed }, 1).toFloat()
        val slot = size.width / days.size
        val barWidth = (slot * BAR_SHARE).coerceAtMost(28.dp.toPx())
        val radius = CornerRadius(6.dp.toPx())
        val segmentGap = 2.dp.toPx()

        // Ziel-Linie hinter den Säulen.
        val goalY = bottom - chartHeight * goal / maxValue
        drawLine(
            color = colors.guide,
            start = Offset(0f, goalY),
            end = Offset(size.width, goalY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )

        days.forEachIndexed { index, day ->
            val (value, weekday, free) = labels[index]
            val left = slot * index + (slot - barWidth) / 2f
            val doneHeight = chartHeight * day.movements / maxValue
            val missedHeight = chartHeight * day.missed / maxValue
            var barTop = bottom
            when {
                free -> Unit
                day.movements + day.missed == 0 -> {
                    // Erinnerungstag ohne Ergebnis: flacher Sockel, damit der Tag sichtbar bleibt.
                    val stub = 4.dp.toPx()
                    drawRoundRect(colors.empty, Offset(left, bottom - stub), Size(barWidth, stub), radius)
                    barTop = bottom - stub
                }

                else -> {
                    if (doneHeight > 0f) {
                        val color = if (day.movements >= goal) colors.done else colors.doneSoft
                        drawRoundRect(color, Offset(left, bottom - doneHeight), Size(barWidth, doneHeight), radius)
                        barTop = bottom - doneHeight
                    }
                    if (missedHeight > 0f) {
                        val missedTop = barTop - (if (doneHeight > 0f) segmentGap else 0f) - missedHeight
                        drawRoundRect(colors.missed, Offset(left, missedTop), Size(barWidth, missedHeight), radius)
                        barTop = missedTop
                    }
                }
            }
            val valueTop = if (free) bottom - value.size.height else barTop - gap - value.size.height
            drawText(value, topLeft = Offset(slot * index + (slot - value.size.width) / 2f, valueTop))
            drawText(weekday, topLeft = Offset(slot * index + (slot - weekday.size.width) / 2f, bottom + gap))
        }
    }
}

/** Anteil der Säulen an ihrer Spalte. */
private const val BAR_SHARE = 0.5f
