package dev.stepcron.wear.ui.schedule

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.ui.WEAR_SMALL_ROUND
import dev.stepcron.wear.ui.formatTimeOfDay
import dev.stepcron.wear.ui.rememberScheduleTexts
import dev.stepcron.wear.ui.theme.DeskBreakTheme
import kotlinx.coroutines.delay
import java.time.DayOfWeek

/** Einstellungen eines Wochentags: an/frei, Beginn, Ende und „Für alle Tage übernehmen". */
@Composable
fun DayScheduleScreen(
    day: DayOfWeek,
    schedule: WeeklySchedule?,
    onEnabledChange: (Boolean) -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onCopyToAll: () -> Unit,
    listState: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
) {
    val texts = rememberScheduleTexts()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var copyState by remember { mutableStateOf(CopyState.IDLE) }
    LaunchedEffect(copyState) {
        when (copyState) {
            CopyState.CONFIRM -> {
                // Mit TalkBack dauert das erneute Antippen länger (erst die Ansage, dann Doppeltippen).
                val touchExploration = context.getSystemService(AccessibilityManager::class.java)
                    ?.isTouchExplorationEnabled == true
                delay(if (touchExploration) CONFIRM_TIMEOUT_A11Y_MILLIS else CONFIRM_TIMEOUT_MILLIS)
                copyState = CopyState.IDLE
            }

            CopyState.DONE, CopyState.SAME -> {
                delay(FEEDBACK_MILLIS)
                copyState = CopyState.IDLE
            }

            CopyState.IDLE -> Unit
        }
    }

    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
        ) {
            item(key = "header") {
                ListHeader { Text(texts.longName(day), color = MaterialTheme.colors.primary) }
            }
            if (schedule != null) {
                val daySchedule = schedule[day]
                item(key = "enabled") {
                    ToggleChip(
                        checked = daySchedule.enabled,
                        onCheckedChange = onEnabledChange,
                        label = {
                            Text(
                                stringResource(R.string.day_schedule_toggle_label),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        secondaryLabel = {
                            Text(
                                stringResource(
                                    if (daySchedule.enabled) R.string.day_schedule_toggle_on else R.string.day_schedule_toggle_off,
                                ),
                            )
                        },
                        toggleControl = { Switch(checked = daySchedule.enabled) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Auch an freien Tagen einstellbar, damit die Zeiten beim Einschalten passen.
                item(key = "start") {
                    TimeChip(
                        label = stringResource(R.string.schedule_start_label),
                        minuteOfDay = daySchedule.window.startMinute,
                        onClick = onEditStart,
                    )
                }
                item(key = "end") {
                    TimeChip(
                        label = stringResource(R.string.schedule_end_label),
                        minuteOfDay = daySchedule.window.endMinute,
                        onClick = onEditEnd,
                    )
                }
                item(key = "copy") {
                    val othersDiffer = schedule.days.any { it.window != daySchedule.window }
                    Chip(
                        onClick = {
                            // Überschreibt bis zu sechs Tage: erst nach einem zweiten Tipp übernehmen.
                            copyState = when {
                                !othersDiffer -> CopyState.SAME
                                copyState == CopyState.CONFIRM -> {
                                    onCopyToAll()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    CopyState.DONE
                                }

                                else -> CopyState.CONFIRM
                            }
                        },
                        label = {
                            Text(
                                stringResource(R.string.day_schedule_copy_label),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = stringResource(
                                    when (copyState) {
                                        CopyState.IDLE -> R.string.day_schedule_copy_secondary
                                        CopyState.CONFIRM -> R.string.day_schedule_copy_confirm
                                        CopyState.DONE -> R.string.day_schedule_copy_done
                                        CopyState.SAME -> R.string.day_schedule_copy_same
                                    },
                                ),
                                maxLines = 2,
                                // Bestätigungsfrage und Ergebnis ansagen, sie ändern nur diese Zeile.
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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

@Composable
private fun TimeChip(label: String, minuteOfDay: Int, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = { Text(formatTimeOfDay(minuteOfDay), maxLines = 1) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

private enum class CopyState { IDLE, CONFIRM, DONE, SAME }

private const val CONFIRM_TIMEOUT_MILLIS = 3_000L
private const val CONFIRM_TIMEOUT_A11Y_MILLIS = 10_000L
private const val FEEDBACK_MILLIS = 2_500L

@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true)
@Composable
private fun DayScheduleScreenPreview() {
    DeskBreakTheme {
        Scaffold(timeText = { TimeText() }) {
            DayScheduleScreen(
                day = DayOfWeek.SATURDAY,
                schedule = WeeklySchedule.DEFAULT,
                onEnabledChange = {},
                onEditStart = {},
                onEditEnd = {},
                onCopyToAll = {},
            )
        }
    }
}
