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
 * Die letzten sieben Tage als Säulen: unten grün die Pausen, darauf rot die verpassten Erinnerungen. Über jeder
 * Säule die Zahl der Pausen, darunter der Wochentag (heute hervorgehoben); gestrichelt das Tagesziel [goal].
 * TalkBack liest alle Tage als eine Beschreibung vor.
 */
@Composable
fun WeekBarChart(days: List<DayStat>, goal: Int, modifier: Modifier = Modifier) {
    val colors = LocalChartColors.current
    val locale = currentLocale()
    val textMeasurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val valueStyle = typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val weekdayStyle = typography.labelSmall.copy(color = colors.label)
    val todayStyle = weekdayStyle.copy(color = colors.today, fontWeight = FontWeight.Bold)

    val dayDescriptions = ArrayList<String>(days.size)
    for (day in days) {
        dayDescriptions += stringResource(
            R.string.cd_week_day,
            Formats.shortDate(day.date, locale),
            breaksText(day.movements),
            day.missed,
        )
    }
    val description = stringResource(R.string.cd_week_chart, goal, dayDescriptions.joinToString("; "))

    // Beschriftungen einmal je Datenstand messen statt bei jedem Zeichnen.
    val labels = remember(days, locale, valueStyle, weekdayStyle, todayStyle, textMeasurer) {
        days.map { day ->
            textMeasurer.measure(day.movements.toString(), valueStyle) to textMeasurer.measure(
                Formats.weekdayShort(day.date.dayOfWeek, locale),
                if (day.isToday) todayStyle else weekdayStyle,
            )
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (days.isEmpty()) return@Canvas
        val gap = 4.dp.toPx()
        val valueHeight = labels.maxOf { it.first.size.height }.toFloat()
        val weekdayHeight = labels.maxOf { it.second.size.height }.toFloat()
        val top = valueHeight + gap
        val bottom = size.height - weekdayHeight - gap
        val chartHeight = (bottom - top).coerceAtLeast(1f)
        val maxValue = maxOf(goal, days.maxOf { it.movements + it.missed }, 1).toFloat()
        val slot = size.width / days.size
        val barWidth = slot * BAR_SHARE
        val radius = CornerRadius(3.dp.toPx())

        days.forEachIndexed { index, day ->
            val left = slot * index + (slot - barWidth) / 2f
            val doneHeight = chartHeight * day.movements / maxValue
            val missedHeight = chartHeight * day.missed / maxValue
            if (day.movements + day.missed == 0) {
                // Kein Ergebnis: flacher grauer Sockel, damit der Tag nicht fehlt.
                val stub = 3.dp.toPx()
                drawRoundRect(colors.empty, Offset(left, bottom - stub), Size(barWidth, stub), radius)
            }
            if (doneHeight > 0f) {
                drawRoundRect(colors.done, Offset(left, bottom - doneHeight), Size(barWidth, doneHeight), radius)
            }
            if (missedHeight > 0f) {
                drawRoundRect(
                    colors.missed,
                    Offset(left, bottom - doneHeight - missedHeight),
                    Size(barWidth, missedHeight),
                    radius,
                )
            }
            val (value, weekday) = labels[index]
            val barTop = bottom - doneHeight - missedHeight
            drawText(value, topLeft = Offset(slot * index + (slot - value.size.width) / 2f, barTop - gap - value.size.height))
            drawText(weekday, topLeft = Offset(slot * index + (slot - weekday.size.width) / 2f, bottom + gap))
        }

        val goalY = bottom - chartHeight * goal / maxValue
        drawLine(
            color = colors.guide,
            start = Offset(0f, goalY),
            end = Offset(size.width, goalY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/** Anteil der Säulen an ihrer Spalte. */
private const val BAR_SHARE = 0.55f
