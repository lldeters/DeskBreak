package dev.stepcron.wear.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import dev.stepcron.wear.ui.WEAR_SMALL_ROUND
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.DaySchedule
import dev.stepcron.wear.domain.DayStat
import dev.stepcron.wear.domain.HourRate
import dev.stepcron.wear.domain.HourSlot
import dev.stepcron.wear.domain.SlotStatus
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.ui.StatsUiState
import dev.stepcron.wear.ui.breaksText
import dev.stepcron.wear.ui.rememberScheduleTexts
import dev.stepcron.wear.ui.charts.DayTimelineChart
import dev.stepcron.wear.ui.charts.MonthHeatmap
import dev.stepcron.wear.ui.charts.WeeklyTrendChart
import dev.stepcron.wear.ui.components.ChartCard
import dev.stepcron.wear.ui.components.ChartLegend
import dev.stepcron.wear.ui.components.SummaryChip
import dev.stepcron.wear.ui.theme.DeskBreakColors
import dev.stepcron.wear.ui.theme.DeskBreakTheme
import dev.stepcron.wear.ui.theme.scoreColor
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Auswertung: Kennzahlen, Tages-Timeline und 7-Tage-Trend. */
@Composable
fun StatsScreen(
    state: StatsUiState,
    /** Heutiger Eintrag des Zeitplans (an/frei, Zeitfenster). */
    today: DaySchedule,
    showDebugTools: Boolean,
    onSeedDemoData: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    // Ein Messer für beide Diagramme auf Bildschirmebene: Sein Cache (rund 25 Beschriftungen) bleibt
    // erhalten, wenn eine Diagrammkarte beim Scrollen aus der Liste fällt und wieder hineinkommt.
    val chartTextMeasurer = rememberTextMeasurer(cacheSize = CHART_TEXT_CACHE_SIZE)
    val texts = rememberScheduleTexts()
    var seeded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(seeded) {
        if (seeded) {
            delay(2_500)
            seeded = false
        }
    }
    LaunchedEffect(confirmClear) {
        if (confirmClear) {
            delay(3_000)
            confirmClear = false
        }
    }

    // Uhrzeit aus dem gemeinsamen Scaffold in DeskBreakNavHost; ohne Vignette (siehe HomeScreen).
    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
        ) {
            item {
                ListHeader {
                    Text(
                        text = stringResource(R.string.stats_title),
                        color = MaterialTheme.colors.primary,
                    )
                }
            }

            // Zusammenfassungs-Karten
            item {
                val percent = state.todayPercent
                val hasDueIntervals = state.todayMovements + state.todayMissed > 0
                val emptyDetail = if (today.enabled) R.string.stats_score_detail_empty else R.string.stats_score_detail_off
                SummaryChip(
                    iconRes = R.drawable.ic_check,
                    title = if (percent != null) {
                        stringResource(R.string.stats_score_value, percent)
                    } else {
                        stringResource(R.string.stats_score_empty)
                    },
                    subtitle = if (hasDueIntervals) {
                        stringResource(
                            R.string.stats_score_detail,
                            breaksText(context, state.todayMovements),
                            state.todayMissed,
                        )
                    } else {
                        stringResource(emptyDetail)
                    },
                    accent = scoreColor(percent),
                )
            }
            item {
                val remaining = (state.dailyGoal - state.todayMovements).coerceAtLeast(0)
                SummaryChip(
                    iconRes = R.drawable.ic_walk,
                    title = stringResource(R.string.stats_goal_value, state.todayMovements, state.dailyGoal),
                    subtitle = if (state.goalReached) {
                        stringResource(R.string.stats_goal_reached)
                    } else {
                        pluralStringResource(R.plurals.stats_goal_remaining, remaining, remaining)
                    },
                    accent = if (state.goalReached) DeskBreakColors.Done else DeskBreakColors.Primary,
                )
            }
            item {
                SummaryChip(
                    iconRes = R.drawable.ic_streak,
                    title = stringResource(R.string.stats_streak_value, state.streak),
                    subtitle = stringResource(R.string.stats_streak_detail),
                    accent = DeskBreakColors.Secondary,
                )
            }

            // 1) Heutige Regelmäßigkeit
            item {
                // An einem freien Tag statt einer leeren Timeline ein Hinweis – außer sie zeigt erfasste Pausen
                // (z. B. „Jetzt bewegt" innerhalb der gespeicherten Zeiten). Während des Ladens bleibt es beim
                // Hinweis, dem an freien Tagen wahrscheinlichsten Ergebnis.
                val timelineHasContent = state.hourSlots.any { it.status != SlotStatus.NONE }
                val showOffDay = !today.enabled && (state.loading || !timelineHasContent)
                ChartCard(
                    title = stringResource(R.string.stats_today_title),
                    subtitle = when {
                        today.enabled -> texts.window(today.window)
                        showOffDay -> stringResource(R.string.stats_today_off_subtitle)
                        else -> stringResource(R.string.stats_today_off_window, texts.window(today.window))
                    },
                ) {
                    if (showOffDay) {
                        Text(
                            text = stringResource(R.string.stats_today_off_hint),
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        DayTimelineChart(
                            slots = state.hourSlots,
                            // Beschriftung passend zu den Slots (volle Stunden, ab der Startstunde).
                            startHour = state.hourSlots.firstOrNull()?.hour ?: (today.window.startMinute / 60),
                            textMeasurer = chartTextMeasurer,
                        )
                        Spacer(Modifier.height(6.dp))
                        ChartLegend(
                            items = listOf(
                                DeskBreakColors.Done to stringResource(R.string.legend_done),
                                DeskBreakColors.Snoozed to stringResource(R.string.legend_snoozed),
                                DeskBreakColors.Missed to stringResource(R.string.legend_missed),
                            ),
                        )
                    }
                }
            }

            // 2) 7-Tage-Trend
            item {
                val weekPercent = state.weekPercent?.let { stringResource(R.string.percent_value, it) }
                    ?: stringResource(R.string.chart_no_data)
                ChartCard(
                    title = stringResource(R.string.stats_week_title),
                    subtitle = stringResource(
                        R.string.stats_week_subtitle,
                        weekPercent,
                        breaksText(context, state.weekMovements),
                    ),
                ) {
                    // Etwas Innenabstand, damit Mo/So am runden Rand nicht abgeschnitten werden.
                    WeeklyTrendChart(
                        days = state.week,
                        modifier = Modifier.padding(horizontal = 6.dp),
                        textMeasurer = chartTextMeasurer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.stats_week_hint),
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 3) Monatsansicht: Tagesziel je Tag der letzten Wochen
            item {
                val description = stringResource(
                    R.string.cd_month_heatmap,
                    state.goalDays,
                    state.daysWithData,
                    state.dailyGoal,
                )
                ChartCard(
                    title = stringResource(R.string.stats_month_title),
                    subtitle = if (state.daysWithData > 0) {
                        stringResource(R.string.stats_month_subtitle, state.goalDays, state.daysWithData)
                    } else {
                        stringResource(R.string.chart_no_data)
                    },
                ) {
                    MonthHeatmap(
                        weeks = state.calendarWeeks,
                        goal = state.dailyGoal,
                        description = description,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.stats_month_hint),
                        style = MaterialTheme.typography.caption3,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // 4) Beste und schwächste Tageszeit
            item {
                val insight = state.timeOfDay
                ChartCard(title = stringResource(R.string.stats_time_of_day_title), subtitle = null) {
                    if (insight == null) {
                        Text(
                            text = stringResource(R.string.stats_time_of_day_empty),
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        TimeOfDayRow(
                            label = stringResource(R.string.stats_time_of_day_best),
                            rate = insight.best,
                            color = scoreColor(insight.best.percent),
                        )
                        Spacer(Modifier.height(4.dp))
                        TimeOfDayRow(
                            label = stringResource(R.string.stats_time_of_day_worst),
                            rate = insight.worst,
                            color = scoreColor(insight.worst.percent),
                        )
                    }
                }
            }

            if (showDebugTools) {
                item { ListHeader { Text(stringResource(R.string.stats_debug_header)) } }
                item {
                    Chip(
                        onClick = {
                            onSeedDemoData()
                            seeded = true
                        },
                        label = { Text(stringResource(R.string.stats_seed_label)) },
                        secondaryLabel = {
                            Text(stringResource(if (seeded) R.string.stats_seed_done else R.string.stats_seed_secondary))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_chart),
                                contentDescription = null,
                                modifier = Modifier.size(ChipDefaults.IconSize),
                            )
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Chip(
                        onClick = {
                            if (confirmClear) {
                                onClearHistory()
                                confirmClear = false
                            } else {
                                confirmClear = true
                            }
                        },
                        label = { Text(stringResource(R.string.stats_clear_label)) },
                        secondaryLabel = if (confirmClear) {
                            { Text(stringResource(R.string.stats_clear_confirm)) }
                        } else {
                            null
                        },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                modifier = Modifier.size(ChipDefaults.IconSize),
                            )
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** „Am besten · 10–11 Uhr · 95 %". */
@Composable
private fun TimeOfDayRow(label: String, rate: HourRate, color: Color) {
    val hours = stringResource(R.string.stats_time_of_day_hours, rate.hour, rate.hour + 1)
    val percent = stringResource(R.string.percent_value, rate.percent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.onSurfaceVariant)
            Text(text = hours, style = MaterialTheme.typography.caption1, color = MaterialTheme.colors.onSurface)
        }
        Text(text = percent, style = MaterialTheme.typography.title3, color = color)
    }
}

@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true)
@Composable
private fun StatsScreenPreview() {
    val today = LocalDate.now()
    val statuses = listOf(
        SlotStatus.DONE, SlotStatus.DONE, SlotStatus.SNOOZED, SlotStatus.DONE, SlotStatus.MISSED,
        SlotStatus.DONE, SlotStatus.NONE, SlotStatus.NONE, SlotStatus.NONE,
    )
    val percents = listOf(82, 75, 91, null, 64, 88, 80)
    DeskBreakTheme {
        // Die Uhrzeit liefert in der App der gemeinsame Scaffold aus DeskBreakNavHost.
        Scaffold(timeText = { TimeText() }) {
            StatsScreen(
                state = StatsUiState(
                    loading = false,
                    todayPercent = 80,
                    todayMovements = 4,
                    todayMissed = 1,
                    streak = 3,
                    hourSlots = statuses.mapIndexed { index, status ->
                        HourSlot(
                            hour = 8 + index,
                            status = status,
                            movementCount = if (status == SlotStatus.DONE) 1 + index % 2 else 0,
                            isCurrent = index == 6,
                            isFuture = index > 6,
                        )
                    },
                    week = percents.mapIndexed { index, percent ->
                        DayStat(
                            date = today.minusDays((6 - index).toLong()),
                            movements = (percent ?: 0) / 10,
                            snoozes = 1,
                            missed = 1,
                            percent = percent,
                            isToday = index == 6,
                        )
                    },
                    weekPercent = 80,
                    weekMovements = 41,
                ),
                today = WeeklySchedule.DEFAULT.days.first(),
                showDebugTools = true,
                onSeedDemoData = {},
                onClearHistory = {},
            )
        }
    }
}

/** Platz für alle Diagramm-Beschriftungen (7 Tage × 3 Texte plus Stundenmarken). */
private const val CHART_TEXT_CACHE_SIZE = 32
