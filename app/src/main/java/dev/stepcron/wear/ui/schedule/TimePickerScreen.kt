package dev.stepcron.wear.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberPickerState
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.ActiveWindow
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.ui.WEAR_SMALL_ROUND
import dev.stepcron.wear.ui.formatTimeOfDay
import dev.stepcron.wear.ui.theme.DeskBreakTheme

/** Auswahlmöglichkeiten der Uhrzeit in 15-Minuten-Schritten (Minuten seit Mitternacht). */
internal object TimeOptions {
    const val STEP_MINUTES = ActiveWindow.MIN_LENGTH_MINUTES

    /** Beginn: 00:00 bis vor dem Ende des Fensters. */
    fun startOptions(window: ActiveWindow): List<Int> = (0 until window.endMinute step STEP_MINUTES).toList()

    /** Ende: erster Schritt nach dem Beginn bis 24:00. */
    fun endOptions(window: ActiveWindow): List<Int> {
        val first = (window.startMinute / STEP_MINUTES + 1) * STEP_MINUTES
        return (first..ActiveWindow.MINUTES_PER_DAY step STEP_MINUTES).toList()
    }

    /** Index der aktuellen Zeit bzw. der nächstspäteren Option. */
    fun initialIndex(options: List<Int>, minuteOfDay: Int): Int =
        options.indexOfFirst { it >= minuteOfDay }.takeIf { it >= 0 } ?: options.lastIndex
}

/**
 * Uhrzeit wählen – mit der Krone bzw. Lünette oder per Wischen – und mit dem Haken übernehmen.
 * Zurückwischen bricht ab.
 */
@Composable
fun TimePickerScreen(
    title: String,
    options: List<Int>,
    initialMinute: Int,
    onConfirm: (Int) -> Unit,
) {
    if (options.isEmpty()) return
    val state = rememberPickerState(
        initialNumberOfOptions = options.size,
        initiallySelectedOption = TimeOptions.initialIndex(options, initialMinute),
        repeatItems = false,
    )
    val selectedMinute by remember(options) {
        derivedStateOf { options[state.selectedOption.coerceIn(options.indices)] }
    }
    val description = stringResource(R.string.cd_time_picker, title, formatTimeOfDay(selectedMinute))

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.caption1, color = MaterialTheme.colors.primary)
            Spacer(Modifier.height(2.dp))
            Picker(
                state = state,
                contentDescription = description,
                modifier = Modifier.size(width = 120.dp, height = 88.dp),
                separation = 4.dp,
            ) { optionIndex ->
                Text(
                    text = formatTimeOfDay(options[optionIndex]),
                    style = MaterialTheme.typography.display3,
                    color = if (optionIndex == selectedOption) {
                        MaterialTheme.colors.onSurface
                    } else {
                        MaterialTheme.colors.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.height(2.dp))
            CompactButton(onClick = { onConfirm(selectedMinute) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.time_picker_confirm),
                    modifier = Modifier.size(ButtonDefaults.SmallIconSize),
                )
            }
        }
    }
}

@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true)
@Composable
private fun TimePickerScreenPreview() {
    DeskBreakTheme {
        Scaffold(timeText = { TimeText() }) {
            TimePickerScreen(
                title = "Aktiv ab",
                options = TimeOptions.startOptions(WeeklySchedule.DEFAULT_WINDOW),
                initialMinute = WeeklySchedule.DEFAULT_WINDOW.startMinute,
                onConfirm = {},
            )
        }
    }
}
