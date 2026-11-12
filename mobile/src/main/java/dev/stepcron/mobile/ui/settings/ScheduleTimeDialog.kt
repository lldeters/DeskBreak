package dev.stepcron.mobile.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.stepcron.mobile.R
import dev.stepcron.mobile.settings.ScheduleRules
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.scheduleIssueText
import dev.stepcron.shared.sync.SyncDay

/**
 * Uhrzeiten eines Tages: „Aktiv ab" und „Aktiv bis" stehen oben nebeneinander, die gewählte wird darunter
 * eingestellt – so ist das ganze Fenster immer sichtbar. Gewählt wird auf 15 Minuten gerundet (wie auf der Uhr);
 * beim Ende steht 00:00 für Mitternacht (24:00). Solange das Fenster kürzer als 15 Minuten wäre, bleibt
 * „Übernehmen" gesperrt und der Grund steht darunter. Optional gelten die Zeiten für alle Tage
 * ([onConfirm] mit `allDays = true`).
 */
@Composable
fun ScheduleTimeDialog(
    dayName: String,
    day: SyncDay,
    allDaysMatch: (startMinute: Int, endMinute: Int) -> Boolean,
    onConfirm: (startMinute: Int, endMinute: Int, allDays: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val portrait = LocalConfiguration.current.let { it.screenHeightDp >= it.screenWidthDp }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            // Hochkant so schmal wie ein Material-Dialog; quer legt der TimePicker die Teile nebeneinander.
            // Ab targetSdk 35 ist auf Android 15+ auch das Dialogfenster edge-to-edge (decorFitsSystemWindows wirkt
            // nicht mehr): Abstand zu Systemleisten, Kamera-Aussparung und Tastatur (Eingabe per Tastatur) selbst
            // halten, sonst liegen quer oder mit Tastatur Titel bzw. Tasten darunter. Ältere Systeme melden hier 0.
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .then(if (portrait) Modifier.widthIn(max = 380.dp) else Modifier),
        ) {
            ScheduleTimeContent(dayName, day, allDaysMatch, onConfirm, onDismiss)
        }
    }
}

/** Inhalt des Dialogs (eigene Funktion, damit Screenshots ihn ohne Dialogfenster zeigen können). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleTimeContent(
    dayName: String,
    day: SyncDay,
    allDaysMatch: (startMinute: Int, endMinute: Int) -> Boolean,
    onConfirm: (startMinute: Int, endMinute: Int, allDays: Boolean) -> Unit,
    onDismiss: () -> Unit,
    initiallyEditingEnd: Boolean = false,
) {
    val initialStart = day.startMinute.coerceIn(0, ScheduleRules.MAX_START_MINUTE)
    val initialEnd = day.endMinute.coerceIn(0, ScheduleRules.MINUTES_PER_DAY) % ScheduleRules.MINUTES_PER_DAY
    val startState = rememberTimePickerState(initialHour = initialStart / 60, initialMinute = initialStart % 60, is24Hour = true)
    val endState = rememberTimePickerState(initialHour = initialEnd / 60, initialMinute = initialEnd % 60, is24Hour = true)
    var editingEnd by rememberSaveable { mutableStateOf(initiallyEditingEnd) }
    var keyboard by rememberSaveable { mutableStateOf(false) }
    var allDays by rememberSaveable { mutableStateOf(false) }

    val start = ScheduleRules.startFromPicker(startState.hour, startState.minute)
    val end = ScheduleRules.endFromPicker(endState.hour, endState.minute)
    val window = SyncDay(enabled = day.enabled, startMinute = start, endMinute = end)
    val issue = if (editingEnd) ScheduleRules.checkEnd(window, end) else ScheduleRules.checkStart(window, start)
    val editedState = if (editingEnd) endState else startState
    val picked = editedState.hour * 60 + editedState.minute
    val minute = if (editingEnd) end else start
    val hint = when {
        issue != null -> scheduleIssueText(issue)
        editingEnd && end == ScheduleRules.MINUTES_PER_DAY -> stringResource(R.string.time_dialog_midnight)
        minute != picked -> stringResource(R.string.time_dialog_snapped, Formats.minuteOfDay(minute))
        else -> stringResource(R.string.time_dialog_steps)
    }
    val nothingToCopy = issue == null && allDaysMatch(start, end)

    // Titel und Felder oben, Hinweis, „Für alle Tage" und Tasten unten bleiben stehen; nur die Uhr scrollt (kleine Handys, quer).
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TimeField(
                    label = stringResource(R.string.schedule_start_label),
                    value = Formats.minuteOfDay(start),
                    selected = !editingEnd,
                    error = issue != null && !editingEnd,
                    onClick = { editingEnd = false },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = stringResource(R.string.schedule_end_label),
                    value = Formats.minuteOfDay(end),
                    selected = editingEnd,
                    error = issue != null && editingEnd,
                    onClick = { editingEnd = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Eigener Zustand je Feld; key baut den Picker beim Wechsel neu auf.
            key(editingEnd, keyboard) { Picker(editedState, keyboard) }
        }
        // Hinweis (erklärt ein gesperrtes „Übernehmen") und „Für alle Tage" bleiben auch auf kleinen Handys sichtbar.
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = if (issue != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
        CopyToAllDays(
            checked = allDays && !nothingToCopy,
            enabled = !nothingToCopy,
            onCheckedChange = { allDays = it },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { keyboard = !keyboard }) {
                Text(stringResource(if (keyboard) R.string.time_dialog_dial else R.string.time_dialog_keyboard))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onConfirm(start, end, allDays && !nothingToCopy) }, enabled = issue == null) {
                Text(stringResource(R.string.apply))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(state: TimePickerState, keyboard: Boolean) {
    if (keyboard) TimeInput(state = state) else TimePicker(state = state)
}

/** „Aktiv ab 08:00" bzw. „Aktiv bis 17:00" als wählbares Feld; das gewählte ist gefüllt und umrandet. */
@Composable
private fun TimeField(
    label: String,
    value: String,
    selected: Boolean,
    error: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.primaryContainer else colors.surfaceContainerHighest
    val content = when {
        error -> colors.error
        selected -> colors.onPrimaryContainer
        else -> colors.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = if (selected) BorderStroke(2.dp, if (error) colors.error else colors.primary) else null,
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
        }
    }
}

/** „Für alle Tage übernehmen" – gesperrt, wenn alle Tage diese Zeiten schon haben. */
@Composable
private fun CopyToAllDays(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.time_dialog_copy),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = stringResource(if (enabled) R.string.time_dialog_copy_hint else R.string.time_dialog_copy_same),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
