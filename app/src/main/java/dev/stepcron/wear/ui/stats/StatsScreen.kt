package dev.stepcron.wear.ui.stats

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.DayStat
import dev.stepcron.wear.domain.HourSlot
import dev.stepcron.wear.domain.SlotStatus
import dev.stepcron.wear.ui.StatsUiState
import dev.stepcron.wear.ui.breaksText
import dev.stepcron.wear.ui.charts.DayTimelineChart
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
    showDebugTools: Boolean,
    onSeedDemoData: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
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

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
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
                        stringResource(R.string.stats_score_detail_empty)
                    },
                    accent = scoreColor(percent),
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
                ChartCard(
                    title = stringResource(R.string.stats_today_title),
                    subtitle = stringResource(R.string.stats_today_subtitle, state.startHour, state.endHour),
                ) {
                    DayTimelineChart(slots = state.hourSlots, startHour = state.startHour)
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
                    WeeklyTrendChart(days = state.week, modifier = Modifier.padding(horizontal = 6.dp))
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
            showDebugTools = true,
            onSeedDemoData = {},
            onClearHistory = {},
        )
    }
}
