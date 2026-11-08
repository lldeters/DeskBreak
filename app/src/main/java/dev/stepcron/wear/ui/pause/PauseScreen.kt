package dev.stepcron.wear.ui.pause

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.PauseOptions
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.domain.toLocalDate
import dev.stepcron.wear.ui.WEAR_SMALL_ROUND
import dev.stepcron.wear.ui.formatClock
import dev.stepcron.wear.ui.rememberResumeText
import dev.stepcron.wear.ui.theme.DeskBreakTheme
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Schnellpause für Meetings oder den Feierabend, dazu der Einstieg in den Urlaub. [onPauseUntil] bekommt das
 * Ende der Pause; [schedule] ist `null`, solange die Einstellungen laden.
 */
@Composable
fun PauseScreen(
    schedule: WeeklySchedule?,
    onPauseUntil: (Long) -> Unit,
    onOpenVacation: () -> Unit,
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    // Minütlich aktualisiert, damit „bis 10:45" stimmt, solange der Bildschirm offen ist.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(MINUTE_MILLIS - value % MINUTE_MILLIS)
            value = System.currentTimeMillis()
        }
    }

    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
        ) {
            item(key = "header") { ListHeader { Text(stringResource(R.string.pause_title)) } }

            PauseOptions.QUICK_MINUTES.forEach { minutes ->
                item(key = "minutes_$minutes") {
                    Chip(
                        // Das Ende ab dem Tipp, nicht ab dem letzten Aktualisieren der Anzeige.
                        onClick = { onPauseUntil(PauseOptions.forMinutes(System.currentTimeMillis(), minutes)) },
                        label = { Text(durationLabel(minutes), maxLines = 1) },
                        secondaryLabel = {
                            Text(
                                stringResource(R.string.pause_until, formatClock(PauseOptions.forMinutes(now, minutes))),
                                maxLines = 1,
                            )
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "rest_of_day") {
                val zone = ZoneId.systemDefault()
                val resumeAt = schedule?.nextActiveAt(PauseOptions.restOfDay(now, zone), zone)
                Chip(
                    onClick = { onPauseUntil(PauseOptions.restOfDay(System.currentTimeMillis(), zone)) },
                    label = { Text(stringResource(R.string.pause_rest_of_day), maxLines = 1) },
                    secondaryLabel = if (resumeAt != null) {
                        {
                            val days = ChronoUnit.DAYS.between(now.toLocalDate(zone), resumeAt.toLocalDate(zone)).toInt()
                            Text(rememberResumeText(resumeAt, days), maxLines = 2)
                        }
                    } else {
                        null
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "vacation") {
                Chip(
                    onClick = onOpenVacation,
                    label = { Text(stringResource(R.string.pause_vacation), maxLines = 1) },
                    secondaryLabel = { Text(stringResource(R.string.pause_vacation_secondary), maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** „30 Minuten", „1 Stunde", „2 Stunden". */
@Composable
private fun durationLabel(minutes: Int): String =
    if (minutes % 60 == 0) {
        pluralStringResource(R.plurals.pause_hours, minutes / 60, minutes / 60)
    } else {
        pluralStringResource(R.plurals.pause_minutes, minutes, minutes)
    }

private const val MINUTE_MILLIS = 60_000L

@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true)
@Composable
private fun PauseScreenPreview() {
    DeskBreakTheme {
        Scaffold(timeText = { TimeText() }) {
            PauseScreen(schedule = WeeklySchedule.DEFAULT, onPauseUntil = {}, onOpenVacation = {})
        }
    }
}
