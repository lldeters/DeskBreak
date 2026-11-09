package dev.stepcron.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.stepcron.mobile.R
import dev.stepcron.mobile.settings.ScheduleIssue
import dev.stepcron.mobile.settings.ScheduleRules
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.scheduleIssueText

/**
 * Uhrzeit für Beginn bzw. Ende ([isEnd]) eines Tages. Die Auswahl wird auf 15 Minuten gerundet (wie auf der Uhr);
 * beim Ende steht 00:00 für Mitternacht (24:00). [validate] prüft den gerundeten Wert gegen die andere Grenze des
 * Tages – solange er nicht passt, bleibt „Übernehmen" gesperrt und der Grund steht darunter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTimeDialog(
    title: String,
    initialMinute: Int,
    isEnd: Boolean,
    validate: (Int) -> ScheduleIssue?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = initialMinute.coerceIn(0, ScheduleRules.MINUTES_PER_DAY) % ScheduleRules.MINUTES_PER_DAY
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    var keyboard by rememberSaveable { mutableStateOf(false) }

    val picked = state.hour * 60 + state.minute
    val minute = if (isEnd) ScheduleRules.endFromPicker(state.hour, state.minute) else ScheduleRules.startFromPicker(state.hour, state.minute)
    val issue = validate(minute)
    val hint = when {
        issue != null -> scheduleIssueText(issue)
        isEnd && minute == ScheduleRules.MINUTES_PER_DAY -> stringResource(R.string.time_dialog_midnight)
        minute != picked -> stringResource(R.string.time_dialog_snapped, Formats.minuteOfDay(minute))
        else -> stringResource(R.string.time_dialog_steps)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            // Ohne feste Breite: Hochkant braucht das Zifferblatt 256 dp, quer legt der TimePicker die Teile nebeneinander.
            modifier = Modifier.padding(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() },
                )
                if (keyboard) TimeInput(state = state) else TimePicker(state = state)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (issue != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { keyboard = !keyboard }) {
                        Text(stringResource(if (keyboard) R.string.time_dialog_dial else R.string.time_dialog_keyboard))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onConfirm(minute) }, enabled = issue == null) {
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}
