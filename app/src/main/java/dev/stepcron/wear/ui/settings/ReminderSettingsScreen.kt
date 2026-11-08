package dev.stepcron.wear.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import dev.stepcron.wear.R
import dev.stepcron.wear.data.settings.ReminderSettings
import dev.stepcron.wear.data.settings.VibrationLevel
import dev.stepcron.wear.ui.WEAR_SMALL_ROUND
import dev.stepcron.wear.ui.components.StepperRow
import dev.stepcron.wear.ui.theme.DeskBreakTheme

/** Was die Einstellungen der Erinnerung zeigen; `null` während des Ladens. */
data class ReminderSettingsUi(
    val snoozeMinutes: Int,
    val vibration: VibrationLevel,
    val stepThreshold: Int,
    /** Schritterkennung an und möglich (Sensor vorhanden). */
    val stepDetectionAvailable: Boolean,
)

/**
 * Snooze-Dauer, Vibrationsstärke (Antippen wechselt und vibriert zur Probe) und Schritt-Schwelle der
 * automatischen Erkennung.
 */
@Composable
fun ReminderSettingsScreen(
    settings: ReminderSettingsUi?,
    onSnoozeStep: (Int) -> Unit,
    onNextVibration: () -> Unit,
    onStepThresholdStep: (Int) -> Unit,
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
        ) {
            item(key = "header") {
                ListHeader {
                    Text(stringResource(R.string.reminder_settings_title), color = MaterialTheme.colors.primary)
                }
            }
            if (settings == null) return@ScalingLazyColumn

            item(key = "snooze") {
                val options = ReminderSettings.SNOOZE_OPTIONS
                StepperRow(
                    label = stringResource(R.string.reminder_settings_snooze),
                    value = stringResource(R.string.reminder_settings_snooze_value, settings.snoozeMinutes),
                    onDecrease = { onSnoozeStep(-1) },
                    onIncrease = { onSnoozeStep(1) },
                    decreaseEnabled = settings.snoozeMinutes > options.first(),
                    increaseEnabled = settings.snoozeMinutes < options.last(),
                    decreaseDescription = stringResource(R.string.cd_snooze_decrease),
                    increaseDescription = stringResource(R.string.cd_snooze_increase),
                )
            }

            item(key = "vibration") {
                Chip(
                    onClick = onNextVibration,
                    label = { Text(stringResource(R.string.reminder_settings_vibration), maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            text = stringResource(vibrationLabel(settings.vibration)),
                            maxLines = 1,
                            // Die neue Stufe ansagen, sie ändert nur diese Zeile.
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "vibration_hint") {
                Text(
                    text = stringResource(R.string.reminder_settings_vibration_hint),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }

            item(key = "steps") {
                val options = ReminderSettings.STEP_THRESHOLD_OPTIONS
                val available = settings.stepDetectionAvailable
                StepperRow(
                    label = stringResource(R.string.reminder_settings_steps),
                    value = stringResource(R.string.reminder_settings_steps_value, settings.stepThreshold),
                    onDecrease = { onStepThresholdStep(-1) },
                    onIncrease = { onStepThresholdStep(1) },
                    decreaseEnabled = available && settings.stepThreshold > options.first(),
                    increaseEnabled = available && settings.stepThreshold < options.last(),
                    decreaseDescription = stringResource(R.string.cd_steps_decrease),
                    increaseDescription = stringResource(R.string.cd_steps_increase),
                )
            }
        }
    }
}

fun vibrationLabel(level: VibrationLevel): Int = when (level) {
    VibrationLevel.OFF -> R.string.vibration_off
    VibrationLevel.LIGHT -> R.string.vibration_light
    VibrationLevel.MEDIUM -> R.string.vibration_medium
    VibrationLevel.STRONG -> R.string.vibration_strong
}

@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true)
@Composable
private fun ReminderSettingsScreenPreview() {
    DeskBreakTheme {
        Scaffold(timeText = { TimeText() }) {
            ReminderSettingsScreen(
                settings = ReminderSettingsUi(
                    snoozeMinutes = 10,
                    vibration = VibrationLevel.STRONG,
                    stepThreshold = 30,
                    stepDetectionAvailable = true,
                ),
                onSnoozeStep = {},
                onNextVibration = {},
                onStepThresholdStep = {},
            )
        }
    }
}
