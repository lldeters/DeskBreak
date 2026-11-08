package dev.stepcron.wear.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import dev.stepcron.wear.ui.WEAR_SMALL_ROUND
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import dev.stepcron.wear.R
import dev.stepcron.wear.ui.HomeStatus
import dev.stepcron.wear.ui.HomeUiState
import dev.stepcron.wear.ui.SystemStatus
import dev.stepcron.wear.ui.components.IntervalProgressBar
import dev.stepcron.wear.ui.components.StepperRow
import dev.stepcron.wear.ui.formatClock
import dev.stepcron.wear.ui.theme.DeskBreakTheme
import kotlinx.coroutines.delay

/** Hauptbildschirm: Status, Aktiv-Schalter, Timer-Reset, Statistik und Einstellungen. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEnabledChange: (Boolean) -> Unit,
    onManualReset: () -> Unit,
    onOpenStats: () -> Unit,
    onIntervalStep: (Int) -> Unit,
    onStartHourStep: (Int) -> Unit,
    onEndHourStep: (Int) -> Unit,
    onAutoStepChange: (Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    listState: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
) {
    val haptics = LocalHapticFeedback.current
    var resetConfirmed by remember { mutableStateOf(false) }
    LaunchedEffect(resetConfirmed) {
        if (resetConfirmed) {
            delay(2_500)
            resetConfirmed = false
        }
    }

    // Die Items lesen den Zustand über ein State-Objekt: So bleibt der Listeninhalt bei einem neuen
    // Zustand (z. B. alle 15 s durch den Countdown) derselbe, die Liste wird nicht neu aufgebaut und
    // nur die Items, die `current` lesen, aktualisieren sich.
    val current by rememberUpdatedState(state)
    // Diese beiden Werte bestimmen, welche Items es überhaupt gibt – deshalb direkt als Wert.
    val showPermissionsChip = state.needsPermissions
    val showExactAlarmChip = !state.system.exactAlarmsAllowed

    // Die Uhrzeit kommt aus dem gemeinsamen Scaffold in DeskBreakNavHost. Bewusst ohne Vignette: Sie
    // dunkelt nur einen schmalen Randstreifen ab, kostet aber zwei bildschirmgroße Bitmaps, die beim
    // Aufbau dekodiert und in jedem Frame über den ganzen Bildschirm geblendet werden.
    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
        ) {
            item(key = "status") { StatusHeader(current) }

            if (showPermissionsChip) {
                item(key = "permissions") {
                    Chip(
                        onClick = onRequestPermissions,
                        label = { Text(stringResource(R.string.home_permissions_label)) },
                        secondaryLabel = { Text(stringResource(R.string.home_permissions_secondary)) },
                        icon = { ChipIcon(R.drawable.ic_warning) },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = Color(0xFF3D2E0A),
                            contentColor = MaterialTheme.colors.secondary,
                            secondaryContentColor = MaterialTheme.colors.onSurface,
                            iconColor = MaterialTheme.colors.secondary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Toggle-Chips ohne App-Icon: Auf kleinen runden Displays bliebe sonst zu wenig Platz
            // und Labels wie „Erinnerungen" würden mitten im Wort umbrechen.
            item(key = "enabled") {
                val enabled = current.enabled
                ToggleChip(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    label = { Text(stringResource(R.string.home_toggle_label), maxLines = 1) },
                    secondaryLabel = {
                        Text(stringResource(if (enabled) R.string.home_toggle_on else R.string.home_toggle_off))
                    },
                    toggleControl = { Switch(checked = enabled) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "reset") {
                Chip(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onManualReset()
                        resetConfirmed = true
                    },
                    label = { Text(stringResource(R.string.home_reset_label)) },
                    secondaryLabel = {
                        Text(stringResource(if (resetConfirmed) R.string.home_reset_done else R.string.home_reset_secondary))
                    },
                    icon = { ChipIcon(R.drawable.ic_walk) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "stats") {
                Chip(
                    onClick = onOpenStats,
                    label = { Text(stringResource(R.string.home_stats_label), maxLines = 2) },
                    icon = { ChipIcon(R.drawable.ic_chart) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item(key = "settings_header") { ListHeader { Text(stringResource(R.string.home_settings_header)) } }

            item(key = "interval") {
                StepperRow(
                    label = stringResource(R.string.home_interval_label),
                    value = stringResource(R.string.home_interval_value, current.intervalMinutes),
                    onDecrease = { onIntervalStep(-1) },
                    onIncrease = { onIntervalStep(1) },
                    decreaseEnabled = current.canDecreaseInterval,
                    increaseEnabled = current.canIncreaseInterval,
                    decreaseDescription = stringResource(R.string.cd_interval_decrease),
                    increaseDescription = stringResource(R.string.cd_interval_increase),
                )
            }
            item(key = "start_hour") {
                StepperRow(
                    label = stringResource(R.string.home_start_label),
                    value = stringResource(R.string.home_hour_value, current.startHour),
                    onDecrease = { onStartHourStep(-1) },
                    onIncrease = { onStartHourStep(1) },
                    decreaseEnabled = current.canDecreaseStart,
                    increaseEnabled = current.canIncreaseStart,
                    decreaseDescription = stringResource(R.string.cd_start_decrease),
                    increaseDescription = stringResource(R.string.cd_start_increase),
                )
            }
            item(key = "end_hour") {
                StepperRow(
                    label = stringResource(R.string.home_end_label),
                    value = stringResource(R.string.home_hour_value, current.endHour),
                    onDecrease = { onEndHourStep(-1) },
                    onIncrease = { onEndHourStep(1) },
                    decreaseEnabled = current.canDecreaseEnd,
                    increaseEnabled = current.canIncreaseEnd,
                    decreaseDescription = stringResource(R.string.cd_end_decrease),
                    increaseDescription = stringResource(R.string.cd_end_increase),
                )
            }

            item(key = "auto_step") {
                // Ohne Schrittsensor wirkt ein eingeschalteter, aber gesperrter Schalter irreführend.
                val sensorAvailable = current.system.stepSensorAvailable
                val autoStepOn = current.autoStepEnabled && sensorAvailable
                // Als Int übergeben, damit die Slot-Lambdas nur bei geändertem Text neu entstehen.
                val statusText = autoStepStatus(current)
                ToggleChip(
                    checked = autoStepOn,
                    onCheckedChange = onAutoStepChange,
                    label = { Text(stringResource(R.string.home_auto_step_label), maxLines = 1) },
                    secondaryLabel = { Text(stringResource(statusText), maxLines = 2) },
                    toggleControl = { Switch(checked = autoStepOn, enabled = sensorAvailable) },
                    enabled = sensorAvailable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (showExactAlarmChip) {
                item(key = "exact_alarm") {
                    Chip(
                        onClick = onOpenExactAlarmSettings,
                        label = { Text(stringResource(R.string.home_exact_alarm_label)) },
                        secondaryLabel = { Text(stringResource(R.string.home_exact_alarm_secondary), maxLines = 2) },
                        icon = { ChipIcon(R.drawable.ic_warning) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(state: HomeUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.caption1,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary,
        )
        Spacer(Modifier.height(2.dp))
        when (val status = if (state.loading) HomeStatus.Planning else state.status) {
            HomeStatus.Disabled -> {
                Headline(stringResource(R.string.home_status_disabled))
                Caption(stringResource(R.string.home_status_disabled_hint))
            }

            HomeStatus.Planning -> Headline(stringResource(R.string.home_status_planning))

            is HomeStatus.Paused -> {
                Headline(stringResource(R.string.home_status_paused))
                val resumeText = if (status.resumesTomorrow) {
                    R.string.home_status_paused_resume_tomorrow
                } else {
                    R.string.home_status_paused_resume_today
                }
                Caption(stringResource(resumeText, formatClock(status.resumeAtMillis)))
            }

            is HomeStatus.Scheduled -> {
                Caption(
                    stringResource(
                        if (status.snoozed) R.string.home_status_snoozed_until else R.string.home_status_next_reminder,
                    ),
                )
                Text(
                    text = formatClock(status.atMillis),
                    style = MaterialTheme.typography.display3,
                    color = MaterialTheme.colors.onSurface,
                )
                Caption(
                    if (status.remainingMinutes <= 0L) {
                        stringResource(R.string.home_status_remaining_now)
                    } else {
                        stringResource(R.string.home_status_remaining, status.remainingMinutes)
                    },
                )
                Spacer(Modifier.height(5.dp))
                IntervalProgressBar(
                    progress = status.progress,
                    color = if (status.snoozed) MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
                )
            }

            is HomeStatus.DayDone -> {
                Headline(stringResource(R.string.home_status_day_done))
                Caption(stringResource(R.string.home_status_day_done_hint, formatClock(status.windowEndMillis)))
            }
        }
        if (state.reminderPending) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_reminder_pending),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Headline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.title3,
        color = MaterialTheme.colors.onSurface,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ChipIcon(iconRes: Int) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(ChipDefaults.IconSize),
    )
}

@StringRes
private fun autoStepStatus(state: HomeUiState): Int = when {
    !state.system.stepSensorAvailable -> R.string.home_auto_step_no_sensor
    !state.system.activityRecognitionGranted -> R.string.home_auto_step_no_permission
    state.autoStepEnabled && state.stepTrackingActive -> R.string.home_auto_step_active
    else -> R.string.home_auto_step_idle
}

@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    DeskBreakTheme {
        // Die Uhrzeit liefert in der App der gemeinsame Scaffold aus DeskBreakNavHost.
        Scaffold(timeText = { TimeText() }) {
            HomeScreen(
                state = HomeUiState(
                    loading = false,
                    enabled = true,
                    status = HomeStatus.Scheduled(
                        atMillis = System.currentTimeMillis() + 32 * 60_000L,
                        remainingMinutes = 32,
                        progress = 0.3f,
                        snoozed = false,
                    ),
                    stepTrackingActive = true,
                    system = SystemStatus(),
                ),
                onEnabledChange = {},
                onManualReset = {},
                onOpenStats = {},
                onIntervalStep = {},
                onStartHourStep = {},
                onEndHourStep = {},
                onAutoStepChange = {},
                onRequestPermissions = {},
                onOpenExactAlarmSettings = {},
            )
        }
    }
}
