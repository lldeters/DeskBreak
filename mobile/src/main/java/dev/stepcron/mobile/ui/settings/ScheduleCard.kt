package dev.stepcron.mobile.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.settings.ScheduleRules
import dev.stepcron.mobile.settings.toLocalDate
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.components.AppCard
import dev.stepcron.mobile.ui.currentLocale
import dev.stepcron.mobile.ui.scheduleIssueText
import dev.stepcron.mobile.ui.theme.LocalChartColors
import dev.stepcron.shared.sync.SyncDay
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Breite des Schalters (Material), damit die Zeitachse genau unter den Balken der Tage steht. */
private val SwitchWidth = 52.dp

/** Abstand zwischen Name und Uhrzeit bzw. zwischen Tagesfläche und Schalter. */
private val Gap = 12.dp

/** Innenabstand der antippbaren Fläche eines Tages. */
private val RegionPadding = 8.dp

/**
 * Wochenplan: je Tag Name und Uhrzeiten, darunter ein Balken über 24 Stunden mit dem aktiven Fenster (freie Tage
 * gestrichelt, heute mit einem Strich für „jetzt"), rechts der Schalter; ganz unten die Stunden als Maßstab.
 * Ein Tipp auf Tag, Balken oder Uhrzeit öffnet die Uhrzeit-Auswahl.
 */
@Composable
internal fun ScheduleCard(
    schedule: List<SyncDay>,
    nowMillis: Long,
    zone: ZoneId,
    onToggle: (dayIndex: Int, enabled: Boolean) -> Unit,
    onEdit: (dayIndex: Int) -> Unit,
) {
    val locale = currentLocale()
    val now = nowMillis.toLocalDate(zone)
    val todayIndex = now.dayOfWeek.value - 1
    val minuteNow = Instant.ofEpochMilli(nowMillis).atZone(zone).let { it.hour * 60 + it.minute }
    val pillWidth = pillWidth()
    AppCard(
        title = stringResource(R.string.settings_schedule_title),
        subtitle = stringResource(R.string.schedule_hint),
    ) {
        ScheduleRules.issues(schedule).forEach { issue ->
            InlineStatus(
                text = scheduleIssueText(issue),
                icon = R.drawable.ic_error,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        BoxWithConstraints {
            // Je Tag: Name und Uhrzeit in einer Zeile, darunter der Balken über die ganze Breite (ohne Schalter).
            val barWidth = maxWidth - RegionPadding - Gap - SwitchWidth
            val nameWidth = barWidth - Gap - pillWidth
            // Volle Wochentage nur, wenn der längste neben die Uhrzeit passt (schmale Handys, große Schrift: „Mi.").
            val fullNames = DayOfWeek.entries.map { Formats.weekdayFull(it, locale) }
            val names = if (widestText(fullNames) <= nameWidth) fullNames else DayOfWeek.entries.map { Formats.weekdayShort(it, locale) }
            Column {
                schedule.forEachIndexed { index, day ->
                    DayRow(
                        name = names[index],
                        fullName = fullNames[index],
                        day = day,
                        isToday = index == todayIndex,
                        minuteNow = minuteNow,
                        pillWidth = pillWidth,
                        onToggle = { onToggle(index, it) },
                        onEdit = { onEdit(index) },
                    )
                }
                HourAxis(hours = if (barWidth >= 200.dp) listOf(0, 6, 12, 18, 24) else listOf(0, 12, 24))
            }
        }
    }
}

/** Breite des breitesten der [texts] im Stil der Tagesnamen. */
@Composable
private fun widestText(texts: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val density = LocalDensity.current
    return remember(texts, style, density) {
        with(density) { texts.maxOf { measurer.measure(it, style).size.width }.toDp() }
    }
}

/** Breite der Uhrzeit-Fläche: gleich für alle Tage, damit die Uhrzeiten eine saubere Spalte bilden. */
@Composable
private fun pillWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
    val dayOff = stringResource(R.string.schedule_day_off)
    val sample = stringResource(R.string.settings_schedule_window, "00:00", "00:00")
    val density = LocalDensity.current
    return remember(style, dayOff, sample, density) {
        val widest = maxOf(measurer.measure(sample, style).size.width, measurer.measure(dayOff, style).size.width)
        with(density) { widest.toDp() } + 24.dp
    }
}

/** Stunden (z. B. 0, 6, 12, 18, 24) unter den Balken – nur zur Orientierung, für TalkBack ohne Bedeutung. */
@Composable
private fun HourAxis(hours: List<Int>) {
    Row(
        modifier = Modifier
            .bleed(start = RegionPadding, end = 0.dp)
            .fillMaxWidth()
            .clearAndSetSemantics {},
    ) {
        Layout(
            content = {
                hours.forEach {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalChartColors.current.label,
                    )
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = RegionPadding),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val width = constraints.maxWidth
            val height = placeables.maxOf { it.height }
            layout(width, height) {
                placeables.forEachIndexed { i, placeable ->
                    val center = width * hours[i] / 24f
                    val x = (center - placeable.width / 2f).roundToInt().coerceIn(0, width - placeable.width)
                    placeable.placeRelative(x, 0)
                }
            }
        }
        // Rechts vom Balken steht in einer Tageszeile nur noch der Schalter.
        Spacer(Modifier.width(Gap + SwitchWidth))
    }
}

@Composable
private fun DayRow(
    name: String,
    fullName: String,
    day: SyncDay,
    isToday: Boolean,
    minuteNow: Int,
    pillWidth: Dp,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val start = Formats.minuteOfDay(day.startMinute)
    val end = Formats.minuteOfDay(day.endMinute)
    val window = stringResource(R.string.settings_schedule_window, start, end)
    val shownName = if (isToday) stringResource(R.string.settings_schedule_today, fullName) else fullName
    val description = if (day.enabled) {
        stringResource(R.string.cd_schedule_day, shownName, start, end)
    } else {
        stringResource(R.string.cd_schedule_day_off, shownName)
    }
    val editLabel = stringResource(R.string.cd_schedule_edit)
    val toggleDescription = stringResource(R.string.cd_day_toggle, fullName)
    Row(
        modifier = Modifier
            .bleed(start = RegionPadding, end = 0.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .then(if (day.enabled) Modifier.clickable(onClick = onEdit) else Modifier)
                .clearAndSetSemantics {
                    contentDescription = description
                    if (day.enabled) {
                        role = Role.Button
                        onClick(label = editLabel) { onEdit(); true }
                    }
                }
                .padding(horizontal = RegionPadding, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isToday) FontWeight.SemiBold else null,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Gap))
                if (day.enabled) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.width(pillWidth),
                    ) {
                        Text(
                            text = window,
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.schedule_day_off),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(pillWidth),
                    )
                }
            }
            DayTimeline(day = day, nowMinute = if (isToday) minuteNow else null)
        }
        Spacer(Modifier.width(Gap))
        Switch(
            checked = day.enabled,
            onCheckedChange = onToggle,
            thumbContent = switchThumb(day.enabled),
            modifier = Modifier.semantics { contentDescription = toggleDescription },
        )
    }
}

/**
 * Balken über den ganzen Tag (0–24 Uhr) mit dem aktiven Fenster; feine Lücken bei 6, 12 und 18 Uhr geben das Maß.
 * Freie Tage: nur eine gestrichelte Linie. [nowMinute] markiert „jetzt" (nur heute).
 */
@Composable
private fun DayTimeline(day: SyncDay, nowMinute: Int?) {
    val colors = LocalChartColors.current
    val gapColor = MaterialTheme.colorScheme.surfaceContainerLow
    val nowColor = MaterialTheme.colorScheme.onSurface
    val freeColor = MaterialTheme.colorScheme.outline
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val total = ScheduleRules.MINUTES_PER_DAY.toFloat()
        val radius = CornerRadius(size.height / 2f)
        if (day.enabled) {
            drawRoundRect(color = colors.track, cornerRadius = radius)
            // Lücken nur in der Spur: Das Fenster bleibt ein durchgehender Balken.
            val gap = 2.dp.toPx()
            for (hour in listOf(6, 12, 18)) {
                val x = size.width * hour / 24f
                drawRect(color = gapColor, topLeft = Offset(x - gap / 2f, 0f), size = Size(gap, size.height))
            }
            val x0 = size.width * day.startMinute / total
            val x1 = size.width * day.endMinute / total
            drawRoundRect(color = colors.done, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height), cornerRadius = radius)
        } else {
            val stroke = 2.dp.toPx()
            drawLine(
                color = freeColor,
                start = Offset(stroke, size.height / 2f),
                end = Offset(size.width - stroke, size.height / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
            )
        }
        if (nowMinute != null) {
            val x = size.width * nowMinute / total
            val stroke = 2.dp.toPx()
            val overhang = 3.dp.toPx()
            drawLine(
                color = nowColor,
                start = Offset(x, -overhang),
                end = Offset(x, size.height + overhang),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
