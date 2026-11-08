package dev.stepcron.wear.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.DayStat
import dev.stepcron.wear.ui.theme.DeskBreakColors
import dev.stepcron.wear.ui.theme.scoreColor

private const val TARGET_PERCENT = 80f

/**
 * 7-Tage-Trend: pro Tag eine Säule mit der Regelmäßigkeits-Quote (Zahl darüber, in %),
 * darunter Wochentag und Anzahl der Bewegungspausen. Gestrichelt: Ziel 80 %.
 */
@Composable
fun WeeklyTrendChart(days: List<DayStat>, modifier: Modifier = Modifier) {
    val weekdayLabels = stringArrayResource(R.array.weekday_short)
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colors
    val valueStyle = TextStyle(color = colors.onSurface, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    val weekdayStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val todayStyle = weekdayStyle.copy(color = colors.primary, fontWeight = FontWeight.Bold)
    val countStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 9.sp)
    val guideColor = colors.onSurfaceVariant.copy(alpha = 0.35f)
    val noData = stringResource(R.string.chart_no_data)
    val description = stringResource(
        R.string.cd_week_trend,
        days.joinToString(", ") { day ->
            val label = weekdayLabels.getOrElse(day.date.dayOfWeek.value - 1) { "" }
            "$label ${day.percent?.let { "$it %" } ?: noData}"
        },
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .semantics { contentDescription = description },
    ) {
        if (days.isEmpty()) return@Canvas
        val columnWidth = size.width / days.size
        val barWidth = minOf(columnWidth * 0.55f, 14.dp.toPx())
        val chartTop = 13.dp.toPx()
        val chartBottom = size.height - 26.dp.toPx()
        val chartHeight = chartBottom - chartTop
        val corner = CornerRadius(minOf(4.dp.toPx(), barWidth / 2f))

        val targetY = chartBottom - chartHeight * TARGET_PERCENT / 100f
        drawLine(
            color = guideColor,
            start = Offset(0f, targetY),
            end = Offset(size.width, targetY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )

        days.forEachIndexed { index, day ->
            val centerX = columnWidth * (index + 0.5f)
            val left = centerX - barWidth / 2f
            drawRoundRect(
                color = DeskBreakColors.Track,
                topLeft = Offset(left, chartTop),
                size = Size(barWidth, chartHeight),
                cornerRadius = corner,
            )

            val percent = day.percent
            val valueLayout = textMeasurer.measure(percent?.toString() ?: "–", valueStyle)
            val valueTop = if (percent != null) {
                val barHeight = (chartHeight * percent / 100f).coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    color = scoreColor(percent),
                    topLeft = Offset(left, chartBottom - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
                chartBottom - barHeight - valueLayout.size.height - 1.dp.toPx()
            } else {
                chartBottom - valueLayout.size.height - 2.dp.toPx()
            }
            drawText(
                valueLayout,
                topLeft = Offset(centerX - valueLayout.size.width / 2f, valueTop.coerceAtLeast(0f)),
            )

            val weekday = weekdayLabels.getOrElse(day.date.dayOfWeek.value - 1) { "" }
            val weekdayLayout = textMeasurer.measure(weekday, if (day.isToday) todayStyle else weekdayStyle)
            val weekdayTop = chartBottom + 2.dp.toPx()
            drawText(weekdayLayout, topLeft = Offset(centerX - weekdayLayout.size.width / 2f, weekdayTop))

            val countLayout = textMeasurer.measure(day.movements.toString(), countStyle)
            drawText(
                countLayout,
                topLeft = Offset(centerX - countLayout.size.width / 2f, weekdayTop + weekdayLayout.size.height),
            )
        }
    }
}
