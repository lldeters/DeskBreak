package dev.stepcron.mobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.stats.CalendarSummary
import dev.stepcron.mobile.stats.Dashboard
import dev.stepcron.mobile.stats.DayStat
import dev.stepcron.mobile.stats.HourRate
import dev.stepcron.mobile.stats.StatsCalculator
import dev.stepcron.mobile.stats.WeekdayStat
import dev.stepcron.mobile.ui.AppUiState
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.breaksText
import dev.stepcron.mobile.ui.charts.CalendarHeatmap
import dev.stepcron.mobile.ui.charts.HourChart
import dev.stepcron.mobile.ui.components.AppCard
import dev.stepcron.mobile.ui.components.ConnectWatchCard
import dev.stepcron.mobile.ui.components.LegendSwatch
import dev.stepcron.mobile.ui.components.Pill
import dev.stepcron.mobile.ui.components.ProblemBanner
import dev.stepcron.mobile.ui.currentLocale
import dev.stepcron.mobile.ui.percentText
import dev.stepcron.mobile.ui.theme.LocalChartColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Verlauf: erst der Überblick (5 Wochen gegen das Tagesziel), dann Muster (Wochentage, Tageszeiten), zuletzt die
 * einzelnen Tage nach Wochen gruppiert. Herunterziehen gleicht ab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(state: AppUiState, modifier: Modifier = Modifier, onSyncNow: () -> Unit = {}) {
    val dashboard = state.data.dashboard
    PullToRefreshBox(isRefreshing = state.sync.syncing, onRefresh = onSyncNow, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val problem = state.sync.problem
            if (problem != null && !state.sync.syncing && state.hasSnapshot) {
                item(key = "problem") { ProblemBanner(problem, onRetry = onSyncNow) }
            }
            when {
                state.loading -> item(key = "loading") {
                    Row(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }

                dashboard == null -> item(key = "connect") {
                    ConnectWatchCard(problem = problem, syncing = state.sync.syncing, onSyncNow = onSyncNow)
                }

                else -> {
                    item(key = "calendar") { CalendarCard(dashboard.calendar, dashboard.dailyGoal) }
                    item(key = "weekdays") { WeekdayCard(dashboard) }
                    item(key = "hours") { HourCard(dashboard) }
                    dayItems(dashboard)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarCard(calendar: CalendarSummary, goal: Int) {
    val locale = currentLocale()
    val days = calendar.weeks.flatten().filterNotNull()
    val range = if (days.isEmpty()) null else stringResource(
        R.string.history_range,
        Formats.dayMonth(days.first().date, locale),
        Formats.dayMonth(days.last().date, locale),
    )
    val summary = pluralStringResource(R.plurals.history_calendar_subtitle, calendar.daysWithData, calendar.goalDays, calendar.daysWithData, goal)
    val colors = LocalChartColors.current
    AppCard(title = stringResource(R.string.history_calendar_title), subtitle = range, icon = R.drawable.ic_event) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.history_goal_days_value, calendar.goalDays, calendar.daysWithData),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = pluralStringResource(R.plurals.history_goal_days_label, goal, goal),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        CalendarHeatmap(weeks = calendar.weeks, goal = goal, description = stringResource(R.string.cd_calendar, summary))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendSwatch(color = colors.done, label = stringResource(R.string.legend_goal_reached))
            LegendSwatch(color = colors.doneSoft, label = stringResource(R.string.legend_some_breaks))
            LegendSwatch(color = colors.empty, label = stringResource(R.string.legend_no_breaks))
            LegendSwatch(color = colors.empty, outline = colors.missed, label = stringResource(R.string.legend_only_missed))
            LegendSwatch(color = Color.Transparent, outline = colors.guide, label = stringResource(R.string.legend_free))
        }
    }
}

/** Durchschnittliche Pausen je Wochentag als liegende Balken mit Ziel-Markierung und dem besten Tag. */
@Composable
private fun WeekdayCard(dashboard: Dashboard) {
    val locale = currentLocale()
    val weekdays = dashboard.weekdays
    val goal = dashboard.dailyGoal
    val best = weekdays.filter { it.daysWithData >= 2 && it.averageMovements != null }.maxByOrNull { it.averageMovements!! }
    AppCard(
        title = stringResource(R.string.history_weekdays_title),
        subtitle = stringResource(R.string.history_weekdays_subtitle),
        icon = R.drawable.ic_bar_chart,
    ) {
        val scale = maxOf(goal.toFloat(), weekdays.maxOf { it.averageMovements ?: 0f }, 1f)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            weekdays.forEach { stat -> WeekdayRow(stat, scale, goal, locale, isBest = stat == best) }
        }
        if (best != null) {
            Pill(
                text = stringResource(R.string.history_weekdays_best, Formats.weekdayFull(best.dayOfWeek, locale)),
                containerColor = LocalChartColors.current.done.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                icon = R.drawable.ic_trending_up,
            )
        }
    }
}

@Composable
private fun WeekdayRow(stat: WeekdayStat, scale: Float, goal: Int, locale: Locale, isBest: Boolean) {
    val colors = LocalChartColors.current
    val average = stat.averageMovements
    val name = Formats.weekdayFull(stat.dayOfWeek, locale)
    val averageText = average?.let { String.format(locale, "%.1f", it) }
    val description = if (averageText == null) {
        stringResource(R.string.cd_weekday_empty, name)
    } else {
        stringResource(R.string.cd_weekday, name, averageText, percentText(stat.percent))
    }
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Formats.weekdayShort(stat.dayOfWeek, locale).take(2),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(32.dp),
            color = if (isBest) colors.done else MaterialTheme.colorScheme.onSurface,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .background(colors.empty, RoundedCornerShape(7.dp)),
        ) {
            if (average != null && average > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((average / scale).coerceIn(0.02f, 1f))
                        .background(if (average >= goal) colors.done else colors.doneSoft, RoundedCornerShape(7.dp)),
                )
            }
            // Ziel-Markierung
            Box(
                modifier = Modifier
                    .fillMaxWidth((goal / scale).coerceIn(0f, 1f))
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(colors.guide))
            }
        }
        Text(
            text = averageText ?: stringResource(R.string.no_value),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 44.dp),
        )
    }
}

/** Pausen und verpasste Erinnerungen je Stunde; beste und schwächste Stunde darunter. */
@Composable
private fun HourCard(dashboard: Dashboard) {
    val insight = dashboard.timeOfDay
    val hourDescriptions = dashboard.hourly.map { stringResource(R.string.cd_hour, it.hour, it.movements, it.missed) }
    val description = stringResource(R.string.cd_hour_chart, hourDescriptions.joinToString("; "))
    AppCard(
        title = stringResource(R.string.history_time_title),
        subtitle = stringResource(R.string.history_time_subtitle),
        icon = R.drawable.ic_schedule,
    ) {
        if (dashboard.hourly.isEmpty()) {
            Text(
                text = stringResource(R.string.history_time_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@AppCard
        }
        HourChart(hourly = dashboard.hourly, highlightHour = insight?.best?.hour, description = description)
        if (insight == null) {
            Text(
                text = stringResource(R.string.history_time_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HourTile(stringResource(R.string.history_time_best), insight.best, LocalChartColors.current.done, Modifier.weight(1f))
                HourTile(stringResource(R.string.history_time_worst), insight.worst, LocalChartColors.current.missed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HourTile(label: String, rate: HourRate, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(
                    R.string.history_time_hours,
                    Formats.minuteOfDay(rate.hour * 60),
                    Formats.minuteOfDay((rate.hour + 1) * 60),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = pluralStringResource(R.plurals.history_time_rate, rate.due, rate.percent, rate.due),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Die letzten 30 Tage, nach Kalenderwochen gruppiert (neueste zuerst). */
private fun LazyListScope.dayItems(dashboard: Dashboard) {
    val weeks = dashboard.lastDays.groupBy { it.date.minusDays(it.date.dayOfWeek.value - 1L) }
    weeks.forEach { (monday, days) ->
        item(key = "week-$monday") { WeekHeader(monday, dashboard.today) }
        item(key = "days-$monday") {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column {
                    days.forEachIndexed { index, day ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        DayRow(day, dashboard.dailyGoal)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(monday: LocalDate, today: LocalDate) {
    val locale = currentLocale()
    val thisMonday = today.minusDays(today.dayOfWeek.value - 1L)
    val text = when (ChronoUnit.WEEKS.between(monday, thisMonday)) {
        0L -> stringResource(R.string.history_week_this)
        1L -> stringResource(R.string.history_week_last)
        else -> stringResource(R.string.history_range, Formats.dayMonth(monday, locale), Formats.dayMonth(monday.plusDays(6), locale))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 4.dp, top = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun DayRow(day: DayStat, goal: Int) {
    val locale = currentLocale()
    val colors = LocalChartColors.current
    val free = !day.scheduled && !day.hasData
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (free) 10.dp else 14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(56.dp)) {
            Text(
                text = Formats.weekdayShort(day.date.dayOfWeek, locale),
                style = MaterialTheme.typography.labelMedium,
                color = if (day.isToday) colors.today else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = Formats.dayMonth(day.date, locale),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (free) {
            Text(
                text = stringResource(R.string.history_day_free),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            return@Row
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (day.hasData) {
                    stringResource(R.string.history_day_detail, breaksText(day.movements), day.missed)
                } else {
                    stringResource(R.string.history_day_no_data)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(colors.empty, RoundedCornerShape(3.dp)),
            ) {
                val share = StatsCalculator.goalShare(day.movements, goal)
                if (share > 0f) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(share)
                            .background(if (day.movements >= goal) colors.done else colors.doneSoft, RoundedCornerShape(3.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        ScoreChip(day.percent)
    }
}

/** Regelmäßigkeit des Tages als farbige Marke (Farbe zusätzlich zur Zahl, nie allein). */
@Composable
private fun ScoreChip(percent: Int?) {
    val colors = LocalChartColors.current
    val color = when {
        percent == null -> MaterialTheme.colorScheme.onSurfaceVariant
        percent >= 80 -> colors.done
        percent >= 50 -> colors.snoozed
        else -> colors.missed
    }
    Box(
        modifier = Modifier
            .widthIn(min = 56.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = percentText(percent), style = MaterialTheme.typography.labelLarge, color = color)
    }
}
