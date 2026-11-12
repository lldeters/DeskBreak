package dev.stepcron.mobile.ui.today

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.stats.Dashboard
import dev.stepcron.mobile.stats.WatchStatus
import dev.stepcron.mobile.stats.WatchStatusInfo
import dev.stepcron.mobile.ui.AppUiState
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.breaksText
import dev.stepcron.mobile.ui.charts.WeekBarChart
import dev.stepcron.mobile.ui.components.AppCard
import dev.stepcron.mobile.ui.components.IconBadge
import dev.stepcron.mobile.ui.components.LegendSwatch
import dev.stepcron.mobile.ui.components.MetricCard
import dev.stepcron.mobile.ui.components.Pill
import dev.stepcron.mobile.ui.components.ProblemBanner
import dev.stepcron.mobile.ui.components.ProgressRing
import dev.stepcron.mobile.ui.components.ConnectWatchCard
import dev.stepcron.mobile.ui.percentText
import dev.stepcron.mobile.ui.phoneDateTimeText
import dev.stepcron.mobile.ui.resumeText
import dev.stepcron.mobile.ui.theme.LocalChartColors
import java.time.ZoneId

/** Was der Heute-Bildschirm auslösen kann. */
data class TodayActions(
    val onSyncNow: () -> Unit = {},
    val onPauseOneHour: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onEnable: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/**
 * Heute – das Wichtigste zuerst: Fortschritt zum Tagesziel und was die Uhr als Nächstes tut (mit der passenden
 * Aktion direkt daneben), dann drei Kennzahlen und die letzten sieben Tage. Der Abgleich läuft im Hintergrund
 * und per Herunterziehen; Probleme erscheinen nur, wenn es welche gibt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(state: AppUiState, actions: TodayActions, modifier: Modifier = Modifier) {
    val data = state.data
    PullToRefreshBox(
        isRefreshing = state.sync.syncing,
        onRefresh = actions.onSyncNow,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val problem = state.sync.problem
            val dashboard = data.dashboard
            val status = data.watchStatus
            if (problem != null && !state.sync.syncing && state.hasSnapshot) {
                item(key = "problem") { ProblemBanner(problem, onRetry = actions.onSyncNow) }
            }
            when {
                state.loading -> item(key = "loading") {
                    val description = stringResource(R.string.loading)
                    Row(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
                    }
                }

                dashboard == null || status == null -> item(key = "connect") {
                    ConnectWatchCard(problem = problem, syncing = state.sync.syncing, onSyncNow = actions.onSyncNow)
                }

                else -> {
                    item(key = "hero") { HeroCard(dashboard, status, data.nowMillis, data.zone, actions) }
                    item(key = "metrics") { MetricsRow(dashboard) }
                    item(key = "week") { WeekCard(dashboard) }
                    item(key = "footer") {
                        val received = data.receivedAtMillis
                        if (received != null) {
                            Text(
                                text = stringResource(R.string.today_footer, phoneDateTimeText(received, data.nowMillis)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Fortschrittsring zum Tagesziel und darunter der Status der Uhr mit der passenden Aktion. */
@Composable
private fun HeroCard(dashboard: Dashboard, info: WatchStatusInfo, nowMillis: Long, zone: ZoneId, actions: TodayActions) {
    val summary = dashboard.summary
    val colors = LocalChartColors.current
    val goalText = if (summary.goalReached) {
        stringResource(R.string.today_goal_reached)
    } else {
        pluralStringResource(R.plurals.today_goal_remaining, summary.remainingToGoal, summary.remainingToGoal)
    }
    val progressDescription = pluralStringResource(R.plurals.cd_today_progress, summary.dailyGoal, summary.movements, summary.dailyGoal)
    AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = "$progressDescription. $goalText" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(
                progress = summary.goalProgress,
                color = colors.done,
                trackColor = colors.track,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = summary.movements.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.today_ring_of, summary.dailyGoal),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = stringResource(R.string.today_breaks_today), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = goalText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (summary.goalReached) colors.done else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StatusSection(info, nowMillis, zone, actions)
    }
}

/** Was die Uhr als Nächstes tut, eine offene Erinnerung und die Aktion, die dazu passt. */
@Composable
private fun StatusSection(info: WatchStatusInfo, nowMillis: Long, zone: ZoneId, actions: TodayActions) {
    val texts = statusTexts(info.status, nowMillis, zone)
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(texts.icon, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = texts.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = texts.headline, style = MaterialTheme.typography.headlineSmall)
            if (texts.detail != null) {
                Text(text = texts.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val pending = info.pendingReminderAtMillis
    if (pending != null) {
        val colors = LocalChartColors.current
        Pill(
            text = stringResource(R.string.status_pending, Formats.clock(pending, zone)),
            containerColor = colors.snoozed.copy(alpha = 0.18f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            icon = R.drawable.ic_walk,
        )
    }
    if (info.outdated) {
        Text(
            text = stringResource(R.string.status_as_of, Formats.clock(info.asOfMillis, zone)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    StatusAction(info.status, actions)
}

/** Die eine Aktion, die zum Status passt: pausieren, fortsetzen, einschalten oder den Wochenplan öffnen. */
@Composable
private fun StatusAction(status: WatchStatus, actions: TodayActions) {
    when (status) {
        is WatchStatus.NextReminder -> ActionButton(R.drawable.ic_pause, R.string.action_pause_hour, actions.onPauseOneHour, primary = false)
        is WatchStatus.Paused -> ActionButton(R.drawable.ic_play, R.string.action_resume, actions.onResume, primary = true)
        WatchStatus.RemindersOff -> ActionButton(R.drawable.ic_play, R.string.action_enable, actions.onEnable, primary = true)
        WatchStatus.NoActiveDays -> ActionButton(R.drawable.ic_event, R.string.action_open_settings, actions.onOpenSettings, primary = true)
        else -> Unit
    }
}

@Composable
private fun ActionButton(@DrawableRes icon: Int, label: Int, onClick: () -> Unit, primary: Boolean) {
    val content: @Composable () -> Unit = {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(label))
    }
    if (primary) {
        Button(onClick = onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        // Umrandet: Auf der getönten Karte bliebe ein getönter Knopf unsichtbar.
        OutlinedButton(onClick = onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding, modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

private data class StatusTexts(@DrawableRes val icon: Int, val label: String, val headline: String, val detail: String?)

@Composable
private fun statusTexts(status: WatchStatus, nowMillis: Long, zone: ZoneId): StatusTexts = when (status) {
    WatchStatus.RemindersOff -> StatusTexts(
        R.drawable.ic_alarm_off,
        stringResource(R.string.status_title),
        stringResource(R.string.status_off),
        stringResource(R.string.status_off_hint),
    )

    WatchStatus.NoActiveDays -> StatusTexts(
        R.drawable.ic_event,
        stringResource(R.string.status_title),
        stringResource(R.string.status_no_days),
        stringResource(R.string.status_no_days_hint),
    )

    is WatchStatus.Paused -> StatusTexts(
        R.drawable.ic_pause,
        stringResource(R.string.status_paused),
        stringResource(R.string.status_paused_until, Formats.clock(status.untilMillis, zone)),
        status.resumeAtMillis?.takeIf { it != status.untilMillis }?.let { resumeText(it, nowMillis, zone) },
    )

    is WatchStatus.OffHours -> StatusTexts(
        R.drawable.ic_timelapse,
        stringResource(if (status.freeToday) R.string.status_day_off else R.string.status_off_hours),
        resumeText(status.resumeAtMillis, nowMillis, zone),
        null,
    )

    is WatchStatus.DayDone -> StatusTexts(
        R.drawable.ic_check_circle,
        stringResource(R.string.status_title),
        stringResource(R.string.status_day_done),
        stringResource(R.string.status_day_done_hint, Formats.clock(status.offHoursFromMillis, zone)),
    )

    is WatchStatus.NextReminder -> {
        val minutes = ((status.atMillis - nowMillis).coerceAtLeast(0L) + 59_999L) / 60_000L
        StatusTexts(
            if (status.snoozed) R.drawable.ic_snooze else R.drawable.ic_schedule,
            stringResource(if (status.snoozed) R.string.status_snoozed_label else R.string.status_next_label),
            Formats.clock(status.atMillis, zone),
            if (minutes <= 1L) stringResource(R.string.status_remaining_now) else stringResource(R.string.status_remaining, minutes),
        )
    }

    WatchStatus.Unknown -> StatusTexts(
        R.drawable.ic_sync,
        stringResource(R.string.status_title),
        stringResource(R.string.status_unknown),
        stringResource(R.string.status_unknown_hint),
    )
}

/** Regelmäßigkeit heute, Serie und verpasste Erinnerungen heute als drei gleich breite Karten. */
@Composable
private fun MetricsRow(dashboard: Dashboard) {
    val summary = dashboard.summary
    val colors = LocalChartColors.current
    // Gleich hohe Karten, auch wenn eine Beschriftung umbricht.
    Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(
            icon = R.drawable.ic_check_circle,
            value = percentText(summary.percent),
            label = stringResource(R.string.metric_regularity),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        MetricCard(
            icon = R.drawable.ic_fire,
            value = dashboard.streak.toString(),
            label = stringResource(R.string.metric_streak),
            accent = colors.snoozed,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        MetricCard(
            icon = R.drawable.ic_alarm_off,
            value = summary.missed.toString(),
            label = stringResource(R.string.metric_missed),
            accent = colors.missed,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/** Die letzten sieben Tage mit Vergleich zur Vorwoche. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekCard(dashboard: Dashboard) {
    val colors = LocalChartColors.current
    AppCard(
        title = stringResource(R.string.week_title),
        subtitle = stringResource(R.string.week_subtitle, percentText(dashboard.weekPercent), breaksText(dashboard.weekMovements)),
        trailing = { TrendPill(dashboard.weekMovements - dashboard.previousWeekMovements) },
    ) {
        WeekBarChart(days = dashboard.week, goal = dashboard.dailyGoal)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendSwatch(color = colors.doneSoft, label = stringResource(R.string.legend_breaks))
            LegendSwatch(color = colors.done, label = stringResource(R.string.legend_goal_reached))
            LegendSwatch(color = colors.missed, label = stringResource(R.string.legend_missed))
            LegendSwatch(color = colors.guide, label = stringResource(R.string.legend_goal_line, dashboard.dailyGoal), dashedLine = true)
        }
    }
}

/** „+4" bzw. „−3" Pausen gegenüber der Vorwoche. */
@Composable
private fun TrendPill(delta: Int) {
    val colors = LocalChartColors.current
    val (icon, color) = when {
        delta > 0 -> R.drawable.ic_trending_up to colors.done
        delta < 0 -> R.drawable.ic_trending_down to colors.missed
        else -> null to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = when {
        delta > 0 -> stringResource(R.string.week_trend_up, delta)
        delta < 0 -> stringResource(R.string.week_trend_down, -delta)
        else -> stringResource(R.string.week_trend_same)
    }
    Pill(
        text = text,
        containerColor = color.copy(alpha = 0.14f),
        contentColor = color,
        icon = icon,
        modifier = Modifier.semantics { contentDescription = text },
    )
}
