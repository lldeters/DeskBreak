package dev.stepcron.wear.ui.schedule

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.SplitToggleChip
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.DaySchedule
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.ui.ScheduleTexts
import dev.stepcron.wear.ui.WEAR_SMALL_ROUND
import dev.stepcron.wear.ui.rememberScheduleTexts
import dev.stepcron.wear.ui.theme.DeskBreakTheme
import java.time.DayOfWeek

/**
 * Wochenplan: ein Eintrag pro Wochentag. Der Schalter schaltet den Tag an oder frei, ein Tipp auf den
 * Eintrag öffnet die Uhrzeiten des Tages. [schedule] ist `null`, solange die Einstellungen laden.
 */
@Composable
fun ScheduleScreen(
    schedule: WeeklySchedule?,
    onDayEnabledChange: (DayOfWeek, Boolean) -> Unit,
    onOpenDay: (DayOfWeek) -> Unit,
    listState: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
) {
    val texts = rememberScheduleTexts()
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
        ) {
            item(key = "header") {
                ListHeader { Text(stringResource(R.string.schedule_title), color = MaterialTheme.colors.primary) }
            }
            item(key = "hint") {
                Text(
                    text = stringResource(R.string.schedule_hint),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (schedule != null) {
                DayOfWeek.entries.forEach { day ->
                    item(key = day.name) {
                        DayChip(
                            day = day,
                            daySchedule = schedule[day],
                            texts = texts,
                            onEnabledChange = { enabled -> onDayEnabledChange(day, enabled) },
                            onClick = { onOpenDay(day) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChip(
    day: DayOfWeek,
    daySchedule: DaySchedule,
    texts: ScheduleTexts,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val name = texts.longName(day)
    // Der Schalterbereich hat sonst kein Label: TalkBack würde siebenmal nur „An, Schalter" ansagen.
    val toggleDescription = stringResource(R.string.cd_day_toggle, name)
    SplitToggleChip(
        checked = daySchedule.enabled,
        onCheckedChange = onEnabledChange,
        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = {
            Text(
                text = if (daySchedule.enabled) {
                    texts.window(daySchedule.window)
                } else {
                    stringResource(R.string.schedule_day_off)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        toggleControl = {
            Switch(
                checked = daySchedule.enabled,
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun previewSchedule() = WeeklySchedule.DEFAULT
    .with(DayOfWeek.THURSDAY) { it.copy(window = ActiveWindow(7 * 60, 15 * 60 + 30)) }

@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true)
@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true, fontScale = 1.24f)
@Composable
private fun ScheduleScreenPreview() {
    DeskBreakTheme {
        Scaffold(timeText = { TimeText() }) {
            ScheduleScreen(schedule = previewSchedule(), onDayEnabledChange = { _, _ -> }, onOpenDay = {})
        }
    }
}
