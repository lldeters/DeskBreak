package dev.stepcron.mobile.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.settings.ScheduleRules
import dev.stepcron.mobile.settings.SettingsEdits
import dev.stepcron.mobile.settings.SettingsRules
import dev.stepcron.mobile.settings.VibrationLevel
import dev.stepcron.mobile.settings.toLocalDate
import dev.stepcron.mobile.ui.AppUiState
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.components.AppCard
import dev.stepcron.mobile.ui.components.ConnectWatchCard
import dev.stepcron.mobile.ui.components.IconBadge
import dev.stepcron.mobile.ui.currentLocale
import dev.stepcron.mobile.ui.phoneDateTimeText
import dev.stepcron.mobile.ui.resumeText
import dev.stepcron.mobile.ui.vibrationText
import dev.stepcron.shared.sync.SyncSettings
import java.time.DayOfWeek
import java.time.ZoneId

/** MIME-Typen, unter denen Dateimanager eine JSON-Sicherung anbieten. */
private val BACKUP_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/**
 * Einstellungen der Uhr, nach Zielen gruppiert: ob und wie oft erinnert wird, kurz pausieren, wann (Wochenplan),
 * wie die Uhr erinnert, Schritterkennung, Daten und zuletzt die Lizenz. Jede Änderung geht sofort an die Uhr; ob
 * sie angekommen ist, zeigt eine Statusanzeige am unteren Rand – nur solange etwas passiert.
 */
@Composable
fun SettingsScreen(
    state: AppUiState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    onOpenUnlock: () -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    onLinkFailed: () -> Unit = {},
) {
    val settings = state.data.settings
    // Welche Datei-Aktion zuletzt gestartet wurde (für den Kreisel an der richtigen Zeile).
    var lastFileAction by rememberSaveable { mutableStateOf<FileAction?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) actions.exportCsv(uri)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) actions.exportBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.loadBackup(uri)
    }

    // Welcher Tag gerade bearbeitet wird (0–6, sonst −1); übersteht das Drehen.
    var editDay by rememberSaveable { mutableIntStateOf(-1) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Unten Platz für die Statusanzeige, damit sie nie etwas dauerhaft verdeckt.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = SendStatusSpace),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.loading -> item(key = "loading") { Loading() }

                settings == null -> {
                    item(key = "connect") {
                        ConnectWatchCard(problem = state.sync.problem, syncing = state.sync.syncing, onSyncNow = actions::syncNow)
                    }
                    item(key = "restoreHint") { Hint(stringResource(R.string.settings_empty_hint)) }
                }

                else -> {
                    val zone = state.data.zone
                    val now = state.data.nowMillis
                    item(key = "reminders") { RemindersCard(settings, now, zone, actions) }
                    item(key = "pause") { PauseCard(settings, now, zone, actions) }
                    item(key = "schedule") {
                        ScheduleCard(
                            schedule = ScheduleRules.normalize(settings.schedule),
                            nowMillis = now,
                            zone = zone,
                            onToggle = actions::setDayEnabled,
                            onEdit = { editDay = it },
                        )
                    }
                    item(key = "watch") { WatchCard(settings, actions) }
                    item(key = "steps") { StepsCard(settings, actions) }
                }
            }
            if (!state.loading) {
                item(key = "data") {
                    DataCard(
                        hasSnapshot = state.hasSnapshot,
                        fileBusy = state.local.fileBusy,
                        lastAction = lastFileAction,
                        restore = state.restore,
                        onExportCsv = {
                            lastFileAction = FileAction.CSV
                            launchSafely(actions) { csvLauncher.launch(actions.csvFileName()) }
                        },
                        onExportBackup = {
                            lastFileAction = FileAction.BACKUP
                            launchSafely(actions) { backupLauncher.launch(actions.backupFileName()) }
                        },
                        onRestore = {
                            lastFileAction = FileAction.RESTORE
                            launchSafely(actions) { restoreLauncher.launch(BACKUP_MIME_TYPES) }
                        },
                        onDismissRestore = actions::dismissRestoreResult,
                    )
                }
            }
            if (!state.loading && state.license.status != null) {
                item(key = "license") {
                    LicenseCard(
                        license = state.license,
                        onOpenUnlock = onOpenUnlock,
                        onRestorePurchases = onRestorePurchases,
                        onLinkFailed = onLinkFailed,
                    )
                }
            }
            if (settings != null) item(key = "footer") { Footer(state) }
        }
        SendStatus(
            state = state.settingsSend,
            onRetry = actions::retrySettings,
            onSync = actions::syncNow,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    val day = settings?.let { ScheduleRules.normalize(it.schedule) }?.getOrNull(editDay)
    if (settings != null && day != null) {
        val dayIndex = editDay
        ScheduleTimeDialog(
            dayName = Formats.weekdayFull(DayOfWeek.of(dayIndex + 1), currentLocale()),
            day = day,
            allDaysMatch = { start, end -> SettingsEdits.allDaysHaveWindow(settings, start, end) },
            onConfirm = { start, end, allDays ->
                if (allDays) actions.setWindowForAllDays(start, end) else actions.setDayWindow(dayIndex, start, end)
                editDay = -1
            },
            onDismiss = { editDay = -1 },
        )
    }

    state.local.pendingRestore?.let { pending ->
        RestoreConfirmDialog(pending, onConfirm = actions::confirmRestore, onDismiss = actions::cancelRestore)
    }
}

/** Öffnet die Dateiauswahl; ohne passende App (sehr selten) eine Meldung statt eines Absturzes. */
private inline fun launchSafely(actions: SettingsActions, launch: () -> Unit) {
    try {
        launch()
    } catch (e: ActivityNotFoundException) {
        actions.onNoFileApp()
    }
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.loading)
    Row(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

// --- Erinnerungen ---

/**
 * Hauptschalter als hervorgehobene Fläche (wie der Hauptschalter in den Android-Einstellungen) mit einem Satz,
 * der sagt, was gerade gilt; darunter Intervall und Tagesziel.
 */
@Composable
private fun RemindersCard(settings: SyncSettings, nowMillis: Long, zone: ZoneId, actions: SettingsActions) {
    AppCard {
        MainSwitch(settings, nowMillis, zone, onCheckedChange = actions::setEnabled)
        Column {
            val interval = SettingsRules.clampInterval(settings.intervalMinutes)
            StepperSettingRow(
                icon = R.drawable.ic_timelapse,
                title = stringResource(R.string.settings_interval),
                subtitle = stringResource(R.string.settings_interval_hint),
                value = interval,
                unit = stringResource(R.string.settings_unit_minutes),
                valueDescription = stringResource(R.string.minutes_value, interval),
                position = (interval - SettingsRules.MIN_INTERVAL_MINUTES) / SettingsRules.INTERVAL_STEP_MINUTES,
                positions = (SettingsRules.MAX_INTERVAL_MINUTES - SettingsRules.MIN_INTERVAL_MINUTES) / SettingsRules.INTERVAL_STEP_MINUTES + 1,
                decreaseLabel = stringResource(R.string.cd_interval_decrease),
                increaseLabel = stringResource(R.string.cd_interval_increase),
                onStep = actions::stepInterval,
            )
            val goal = SettingsRules.clampDailyGoal(settings.dailyGoal)
            StepperSettingRow(
                icon = R.drawable.ic_flag,
                title = stringResource(R.string.settings_goal),
                subtitle = stringResource(R.string.settings_goal_hint),
                value = goal,
                unit = pluralStringResource(R.plurals.settings_unit_breaks, goal),
                valueDescription = pluralStringResource(R.plurals.breaks_count, goal, goal),
                position = goal - SettingsRules.MIN_DAILY_GOAL,
                positions = SettingsRules.MAX_DAILY_GOAL - SettingsRules.MIN_DAILY_GOAL + 1,
                decreaseLabel = stringResource(R.string.cd_goal_decrease),
                increaseLabel = stringResource(R.string.cd_goal_increase),
                onStep = actions::stepDailyGoal,
            )
        }
    }
}

@Composable
private fun MainSwitch(settings: SyncSettings, nowMillis: Long, zone: ZoneId, onCheckedChange: (Boolean) -> Unit) {
    val on = settings.enabled
    val summary = when {
        !on -> stringResource(R.string.settings_main_off)
        SettingsRules.isPaused(settings, nowMillis) -> pauseUntilText(settings.pausedUntilMillis, nowMillis, zone)
        ScheduleRules.issues(settings.schedule).isNotEmpty() -> stringResource(R.string.settings_main_no_days)
        else -> stringResource(R.string.settings_main_on, SettingsRules.clampInterval(settings.intervalMinutes))
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (on) colors.secondaryContainer else colors.surfaceContainerHighest,
        contentColor = if (on) colors.onSecondaryContainer else colors.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = on, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { heading() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = if (on) R.drawable.ic_alarm else R.drawable.ic_alarm_off,
                tint = if (on) colors.primary else colors.onSurfaceVariant,
                size = 44.dp,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(text = stringResource(R.string.settings_enabled), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (on) colors.onSecondaryContainer else colors.onSurfaceVariant,
                )
            }
            Switch(checked = on, onCheckedChange = null, thumbContent = switchThumb(on))
        }
    }
}

// --- Pause ---

/**
 * Kurz pausieren: läuft eine Pause, steht bis wann und „Jetzt fortsetzen" als Hauptaktion; sonst die zwei
 * Schnellpausen. Bei ausgeschalteten Erinnerungen gesperrt – mit Erklärung statt stillem Grau.
 */
@Composable
private fun PauseCard(settings: SyncSettings, nowMillis: Long, zone: ZoneId, actions: SettingsActions) {
    val paused = SettingsRules.isPaused(settings, nowMillis)
    val subtitle = when {
        paused -> null
        settings.enabled -> stringResource(R.string.pause_hint)
        else -> stringResource(R.string.settings_pause_off)
    }
    AppCard(title = stringResource(R.string.settings_section_pause), subtitle = subtitle) {
        if (paused) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(R.drawable.ic_pause, size = 40.dp)
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = pauseUntilText(settings.pausedUntilMillis, nowMillis, zone), style = MaterialTheme.typography.titleMedium)
                    if (settings.enabled) {
                        val resume = ScheduleRules.nextActiveAt(settings.schedule, settings.pausedUntilMillis, zone)
                        if (resume != null && resume != settings.pausedUntilMillis) {
                            Text(
                                text = resumeText(resume, nowMillis, zone),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = actions::endPause,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.pause_end), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        } else {
            val hourDescription = stringResource(R.string.action_pause_hour)
            // Gleich hoch, auch wenn eine Beschriftung umbricht (schmale Handys, große Schrift).
            Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Ausschalten und Pausieren in einer Änderung würde die Uhr ignorieren – und ist ohnehin sinnlos.
                OutlinedButton(
                    onClick = actions::pauseOneHour,
                    enabled = settings.enabled,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics { contentDescription = hourDescription },
                ) {
                    Text(text = stringResource(R.string.pause_one_hour), textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = actions::pauseRestOfDay,
                    enabled = settings.enabled,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Text(text = stringResource(R.string.pause_rest_of_day), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** „Pausiert bis 17:00", „… bis morgen, 08:00" oder „… bis Mo., 24. Nov., 08:00" (Zeitzone der Uhr). */
@Composable
private fun pauseUntilText(untilMillis: Long, nowMillis: Long, zone: ZoneId): String {
    val date = untilMillis.toLocalDate(zone)
    val today = nowMillis.toLocalDate(zone)
    val time = Formats.clock(untilMillis, zone)
    return when (date) {
        today -> stringResource(R.string.pause_until_today, time)
        today.plusDays(1) -> stringResource(R.string.pause_until_tomorrow, time)
        else -> stringResource(R.string.pause_until_date, Formats.shortDate(date, currentLocale()), time)
    }
}

// --- Auf der Uhr ---

/** Wie die Uhr erinnert: Snooze, Vibration, Ruhemodi, nur am Handgelenk. */
@Composable
private fun WatchCard(settings: SyncSettings, actions: SettingsActions) {
    AppCard(title = stringResource(R.string.settings_watch_title), subtitle = stringResource(R.string.settings_watch_subtitle)) {
        Column {
            val snooze = SettingsRules.nearestSnooze(settings.snoozeMinutes)
            StepperSettingRow(
                icon = R.drawable.ic_snooze,
                title = stringResource(R.string.settings_snooze),
                subtitle = stringResource(R.string.settings_snooze_until),
                value = snooze,
                unit = stringResource(R.string.settings_unit_minutes),
                valueDescription = stringResource(R.string.minutes_value, snooze),
                position = SettingsRules.SNOOZE_OPTIONS.indexOf(snooze),
                positions = SettingsRules.SNOOZE_OPTIONS.size,
                decreaseLabel = stringResource(R.string.cd_snooze_decrease),
                increaseLabel = stringResource(R.string.cd_snooze_increase),
                onStep = actions::stepSnooze,
            )
            ChoiceSettingRow(
                icon = R.drawable.ic_vibration,
                title = stringResource(R.string.settings_vibration),
                subtitle = null,
                options = VibrationLevel.entries,
                selected = SettingsRules.vibrationOf(settings.vibration),
                label = { vibrationText(it) },
                onSelect = actions::setVibration,
            )
            SwitchSettingRow(
                icon = R.drawable.ic_dnd,
                title = stringResource(R.string.settings_quiet),
                subtitle = stringResource(R.string.settings_quiet_hint),
                checked = settings.respectQuietModes,
                onCheckedChange = actions::setRespectQuietModes,
            )
            SwitchSettingRow(
                icon = R.drawable.ic_watch,
                title = stringResource(R.string.settings_worn),
                subtitle = stringResource(R.string.settings_worn_hint),
                checked = settings.onlyWhenWorn,
                onCheckedChange = actions::setOnlyWhenWorn,
            )
        }
    }
}

// --- Schritterkennung ---

/** Gehen zählt als Pause; die Schwelle gilt nur bei eingeschalteter Erkennung (sonst gesperrt). */
@Composable
private fun StepsCard(settings: SyncSettings, actions: SettingsActions) {
    AppCard(title = stringResource(R.string.settings_steps_title)) {
        Column {
            SwitchSettingRow(
                icon = R.drawable.ic_walk,
                title = stringResource(R.string.settings_auto_steps),
                subtitle = stringResource(R.string.settings_auto_steps_hint),
                checked = settings.autoStepEnabled,
                onCheckedChange = actions::setAutoSteps,
            )
            val threshold = SettingsRules.nearestStepThreshold(settings.stepThreshold)
            StepperSettingRow(
                icon = R.drawable.ic_speed,
                title = stringResource(R.string.settings_threshold),
                subtitle = stringResource(R.string.settings_threshold_hint),
                value = threshold,
                unit = pluralStringResource(R.plurals.settings_unit_steps, threshold),
                valueDescription = pluralStringResource(R.plurals.steps_count, threshold, threshold),
                position = SettingsRules.STEP_THRESHOLD_OPTIONS.indexOf(threshold),
                positions = SettingsRules.STEP_THRESHOLD_OPTIONS.size,
                decreaseLabel = stringResource(R.string.cd_steps_decrease),
                increaseLabel = stringResource(R.string.cd_steps_increase),
                onStep = actions::stepStepThreshold,
                enabled = settings.autoStepEnabled,
            )
        }
    }
}

// --- Fußzeile ---

@Composable
private fun Footer(state: AppUiState) {
    val received = state.data.receivedAtMillis
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Hint(stringResource(R.string.send_idle))
        if (received != null) Hint(stringResource(R.string.settings_footer_synced, phoneDateTimeText(received, state.data.nowMillis)))
    }
}
