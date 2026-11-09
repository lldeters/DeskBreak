package dev.stepcron.mobile.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.stats.CalendarSummary
import dev.stepcron.mobile.stats.DayStat
import dev.stepcron.mobile.stats.HourRate
import dev.stepcron.mobile.stats.TimeOfDayInsight
import dev.stepcron.mobile.ui.AppUiState
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.breaksText
import dev.stepcron.mobile.ui.charts.CalendarHeatmap
import dev.stepcron.mobile.ui.components.LegendSwatch
import dev.stepcron.mobile.ui.components.NoticeCard
import dev.stepcron.mobile.ui.components.SectionCard
import dev.stepcron.mobile.ui.components.SectionHeader
import dev.stepcron.mobile.ui.currentLocale
import dev.stepcron.mobile.ui.percentText
import dev.stepcron.mobile.ui.problemText
import dev.stepcron.mobile.ui.theme.LocalChartColors

/** Verlauf: 5-Wochen-Ansicht, beste und schwächste Tageszeit und die letzten 30 Tage. */
@Composable
fun HistoryScreen(state: AppUiState, modifier: Modifier = Modifier) {
    val dashboard = state.data.dashboard
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (dashboard == null) {
            item(key = "empty") {
                val text = when {
                    state.loading -> stringResource(R.string.loading)
                    state.sync.syncing -> stringResource(R.string.empty_syncing)
                    else -> state.sync.problem?.let { problemText(it) } ?: stringResource(R.string.problem_no_data)
                }
                SectionCard(title = stringResource(R.string.empty_title)) {
                    NoticeCard(text = text, isError = false)
                    Text(
                        text = stringResource(R.string.history_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }
        item(key = "calendar") { CalendarCard(dashboard.calendar, dashboard.dailyGoal) }
        item(key = "timeOfDay") { TimeOfDayCard(dashboard.timeOfDay) }
        item(key = "daysHeader") { SectionHeader(stringResource(R.string.history_days_title)) }
        items(dashboard.lastDays, key = { it.date.toEpochDay() }) { day -> DayRow(day) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarCard(calendar: CalendarSummary, goal: Int) {
    val subtitle = pluralStringResource(
        R.plurals.history_calendar_subtitle,
        calendar.daysWithData,
        calendar.goalDays,
        calendar.daysWithData,
        goal,
    )
    val description = stringResource(R.string.cd_calendar, subtitle)
    val colors = LocalChartColors.current
    SectionCard(title = stringResource(R.string.history_calendar_title), subtitle = subtitle) {
        CalendarHeatmap(weeks = calendar.weeks, goal = goal, description = description)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendSwatch(color = colors.empty, label = stringResource(R.string.legend_no_breaks))
            LegendSwatch(color = colors.done.copy(alpha = 0.45f), label = stringResource(R.string.legend_some_breaks))
            LegendSwatch(color = colors.done, label = stringResource(R.string.legend_goal_reached))
            LegendSwatch(color = colors.empty, outline = colors.missed, label = stringResource(R.string.legend_only_missed))
        }
    }
}

@Composable
private fun TimeOfDayCard(insight: TimeOfDayInsight?) {
    SectionCard(
        title = stringResource(R.string.history_time_title),
        subtitle = stringResource(R.string.history_time_subtitle),
    ) {
        if (insight == null) {
            Text(
                text = stringResource(R.string.history_time_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            HourRow(label = stringResource(R.string.history_time_best), rate = insight.best)
            HourRow(label = stringResource(R.string.history_time_worst), rate = insight.worst)
        }
    }
}

@Composable
private fun HourRow(label: String, rate: HourRate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(
                    R.string.history_time_hours,
                    Formats.minuteOfDay(rate.hour * 60),
                    Formats.minuteOfDay((rate.hour + 1) * 60),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = pluralStringResource(R.plurals.history_time_rate, rate.due, rate.percent, rate.due),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DayRow(day: DayStat) {
    val locale = currentLocale()
    val date = Formats.shortDate(day.date, locale)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (day.isToday) stringResource(R.string.history_day_today, date) else date,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    text = if (day.hasData) {
                        stringResource(R.string.history_day_detail, breaksText(day.movements), day.missed)
                    } else {
                        stringResource(R.string.history_day_no_data)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(text = percentText(day.percent), style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()
    }
}
