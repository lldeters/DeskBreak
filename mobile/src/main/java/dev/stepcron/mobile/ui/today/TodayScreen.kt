package dev.stepcron.mobile.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.stats.Dashboard
import dev.stepcron.mobile.stats.WatchStatus
import dev.stepcron.mobile.stats.WatchStatusInfo
import dev.stepcron.mobile.sync.SyncProblem
import dev.stepcron.mobile.ui.AppUiState
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.breaksText
import dev.stepcron.mobile.ui.charts.WeekBarChart
import dev.stepcron.mobile.ui.components.LegendSwatch
import dev.stepcron.mobile.ui.components.NoticeCard
import dev.stepcron.mobile.ui.components.SectionCard
import dev.stepcron.mobile.ui.components.StatTile
import dev.stepcron.mobile.ui.isError
import dev.stepcron.mobile.ui.percentText
import dev.stepcron.mobile.ui.phoneDateTimeText
import dev.stepcron.mobile.ui.problemText
import dev.stepcron.mobile.ui.resumeText
import dev.stepcron.mobile.ui.theme.LocalChartColors
import java.time.ZoneId

/** Heute: Abgleich, Status der Uhr, Pausen gegen das Tagesziel, Regelmäßigkeit, Serie und die letzten 7 Tage. */
@Composable
fun TodayScreen(state: AppUiState, onSyncNow: () -> Unit, modifier: Modifier = Modifier) {
    val data = state.data
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "sync") { SyncCard(state, onSyncNow) }
        val dashboard = data.dashboard
        val status = data.watchStatus
        when {
            state.loading -> item(key = "loading") { LoadingCard() }
            dashboard == null || status == null -> item(key = "empty") { NoDataCard(state.sync.problem, state.sync.syncing) }
            else -> {
                item(key = "status") { WatchStatusCard(status, data.nowMillis, data.zone) }
                item(key = "today") { TodayCard(dashboard) }
                item(key = "week") { WeekCard(dashboard) }
            }
        }
    }
}

@Composable
private fun SyncCard(state: AppUiState, onSyncNow: () -> Unit) {
    val sync = state.sync
    val received = state.data.receivedAtMillis
    val syncedText = if (received != null) {
        stringResource(R.string.sync_last, phoneDateTimeText(received, state.data.nowMillis))
    } else {
        stringResource(R.string.sync_never)
    }
    SectionCard(title = stringResource(R.string.sync_title), subtitle = syncedText) {
        if (sync.syncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.sync_running),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        val problem = sync.problem
        // Ohne Daten erklärt die Karte darunter das Problem ausführlicher.
        if (problem != null && !sync.syncing && state.hasSnapshot) {
            NoticeCard(text = problemText(problem), isError = problem.isError())
        }
        FilledTonalButton(
            onClick = onSyncNow,
            enabled = !sync.syncing,
            modifier = Modifier.align(Alignment.End),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = stringResource(R.string.sync_now))
        }
    }
}

@Composable
private fun LoadingCard() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        val description = stringResource(R.string.loading)
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
    }
}

/** Noch kein Stand der Uhr: erklärt, was zu tun ist. */
@Composable
private fun NoDataCard(problem: SyncProblem?, syncing: Boolean) {
    val text = when {
        syncing -> stringResource(R.string.empty_syncing)
        problem != null -> problemText(problem)
        else -> stringResource(R.string.problem_no_data)
    }
    SectionCard(title = stringResource(R.string.empty_title)) {
        NoticeCard(text = text, isError = problem?.isError() == true && !syncing)
        Text(
            text = stringResource(R.string.empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WatchStatusCard(info: WatchStatusInfo, nowMillis: Long, zone: ZoneId) {
    val (headline, detail) = statusTexts(info.status, nowMillis, zone)
    val asOf = if (info.outdated) stringResource(R.string.status_as_of, Formats.clock(info.asOfMillis, zone)) else null
    SectionCard(title = stringResource(R.string.status_title), subtitle = asOf) {
        Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(text = headline, style = MaterialTheme.typography.headlineSmall)
            if (detail != null) {
                Text(text = detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val pending = info.pendingReminderAtMillis
        if (pending != null) {
            NoticeCard(
                text = stringResource(R.string.status_pending, Formats.clock(pending, zone)),
                isError = false,
            )
        }
    }
}

/** Überschrift und Unterzeile des Status der Uhr. */
@Composable
private fun statusTexts(status: WatchStatus, nowMillis: Long, zone: ZoneId): Pair<String, String?> = when (status) {
    WatchStatus.RemindersOff -> stringResource(R.string.status_off) to stringResource(R.string.status_off_hint)
    WatchStatus.NoActiveDays -> stringResource(R.string.status_no_days) to stringResource(R.string.status_no_days_hint)
    is WatchStatus.Paused -> stringResource(R.string.status_paused) to (
        status.resumeAtMillis?.let { resumeText(it, nowMillis, zone) }
            ?: stringResource(R.string.status_paused_until, Formats.clock(status.untilMillis, zone))
        )

    is WatchStatus.OffHours -> stringResource(
        if (status.freeToday) R.string.status_day_off else R.string.status_off_hours,
    ) to resumeText(status.resumeAtMillis, nowMillis, zone)

    is WatchStatus.DayDone -> stringResource(R.string.status_day_done) to
        stringResource(R.string.status_day_done_hint, Formats.clock(status.offHoursFromMillis, zone))

    is WatchStatus.NextReminder -> {
        val minutes = ((status.atMillis - nowMillis).coerceAtLeast(0L) + 59_999L) / 60_000L
        val title = stringResource(
            if (status.snoozed) R.string.status_snoozed_until else R.string.status_next_reminder,
            Formats.clock(status.atMillis, zone),
        )
        title to if (minutes <= 1L) {
            stringResource(R.string.status_remaining_now)
        } else {
            stringResource(R.string.status_remaining, minutes)
        }
    }

    WatchStatus.Unknown -> stringResource(R.string.status_unknown) to stringResource(R.string.status_unknown_hint)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayCard(dashboard: Dashboard) {
    val summary = dashboard.summary
    val goalText = if (summary.goalReached) {
        stringResource(R.string.today_goal_reached)
    } else {
        pluralStringResource(R.plurals.today_goal_remaining, summary.remainingToGoal, summary.remainingToGoal)
    }
    SectionCard(title = stringResource(R.string.today_title)) {
        val progressDescription = pluralStringResource(
            R.plurals.cd_today_progress,
            summary.dailyGoal,
            summary.movements,
            summary.dailyGoal,
        )
        Column(
            modifier = Modifier.clearAndSetSemantics { contentDescription = "$progressDescription. $goalText" },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.today_goal_value, summary.movements, summary.dailyGoal),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = pluralStringResource(R.plurals.today_breaks_label, summary.dailyGoal),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { summary.goalProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = LocalChartColors.current.done,
            )
            Text(text = goalText, style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(value = percentText(summary.percent), label = stringResource(R.string.today_regularity))
            StatTile(value = summary.missed.toString(), label = stringResource(R.string.today_missed))
            StatTile(value = summary.snoozes.toString(), label = stringResource(R.string.today_snoozes))
            StatTile(value = dashboard.streak.toString(), label = stringResource(R.string.today_streak))
        }
        if (summary.percent == null) {
            Text(
                text = stringResource(R.string.today_nothing_due),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekCard(dashboard: Dashboard) {
    val subtitle = stringResource(
        R.string.week_subtitle,
        percentText(dashboard.weekPercent),
        breaksText(dashboard.weekMovements),
    )
    val colors = LocalChartColors.current
    SectionCard(title = stringResource(R.string.week_title), subtitle = subtitle) {
        WeekBarChart(days = dashboard.week, goal = dashboard.dailyGoal)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendSwatch(color = colors.done, label = stringResource(R.string.legend_breaks))
            LegendSwatch(color = colors.missed, label = stringResource(R.string.legend_missed))
            LegendSwatch(color = colors.guide, label = stringResource(R.string.legend_goal_line, dashboard.dailyGoal))
        }
    }
}
