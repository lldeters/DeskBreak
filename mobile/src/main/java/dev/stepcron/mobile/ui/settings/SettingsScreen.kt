package dev.stepcron.mobile.ui.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.settings.ScheduleRules
import dev.stepcron.mobile.settings.SettingsRules
import dev.stepcron.mobile.settings.VibrationLevel
import dev.stepcron.mobile.settings.toLocalDate
import dev.stepcron.mobile.stats.BackupSummary
import dev.stepcron.mobile.sync.RestoreState
import dev.stepcron.mobile.sync.SettingsSendState
import dev.stepcron.mobile.ui.AppUiState
import dev.stepcron.mobile.ui.AppViewModel
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.PendingRestore
import dev.stepcron.mobile.ui.components.NoticeCard
import dev.stepcron.mobile.ui.components.SectionHeader
import dev.stepcron.mobile.ui.components.SegmentedChoice
import dev.stepcron.mobile.ui.components.StepperRow
import dev.stepcron.mobile.ui.components.SwitchRow
import dev.stepcron.mobile.ui.currentLocale
import dev.stepcron.mobile.ui.problemText
import dev.stepcron.mobile.ui.scheduleIssueText
import dev.stepcron.mobile.ui.vibrationText
import dev.stepcron.shared.sync.SyncDay
import dev.stepcron.shared.sync.SyncSettings
import java.time.DayOfWeek
import java.time.ZoneId

/** MIME-Typen, unter denen Dateimanager eine JSON-Sicherung anbieten. */
private val BACKUP_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/**
 * Einstellungen der Uhr. Jede Änderung geht sofort als SettingsUpdate an die Uhr (danach wird um einen neuen
 * Stand gebeten); oben steht, ob sie angekommen und bestätigt ist. Darunter Export und Sicherung.
 */
@Composable
fun SettingsScreen(state: AppUiState, viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val settings = state.data.settings
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsv(uri)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadBackup(uri)
    }

    // Welche Uhrzeit gerade bearbeitet wird (Tag 0–6, sonst −1); übersteht das Drehen.
    var editDay by rememberSaveable { mutableIntStateOf(-1) }
    var editEnd by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item(key = "send") {
            SendStatus(state.settingsSend, onRetry = viewModel::retrySettings, onSync = viewModel::syncNow)
        }
        if (settings == null) {
            item(key = "noSettings") { NoSettings(state, onSync = viewModel::syncNow) }
        } else {
            reminderItems(settings, viewModel)
            pauseItems(settings, state.data.nowMillis, state.data.zone, viewModel)
            scheduleItems(settings, onToggle = viewModel::setDayEnabled, onEdit = { day, end ->
                editDay = day
                editEnd = end
            })
            alertItems(settings, viewModel)
        }
        item(key = "dataHeader") { SectionHeader(stringResource(R.string.settings_section_data)) }
        item(key = "data") {
            val hasData = state.hasSnapshot && !state.local.fileBusy
            DataSection(
                hasData = hasData,
                restore = state.restore,
                restoreEnabled = !state.local.fileBusy && state.restore !is RestoreState.Sending &&
                    state.restore !is RestoreState.WaitingForWatch,
                busy = state.local.fileBusy,
                onExportCsv = { launchSafely(viewModel) { csvLauncher.launch(viewModel.csvFileName()) } },
                onExportBackup = { launchSafely(viewModel) { backupLauncher.launch(viewModel.backupFileName()) } },
                onRestore = { launchSafely(viewModel) { restoreLauncher.launch(BACKUP_MIME_TYPES) } },
                onDismissRestore = viewModel::dismissRestoreResult,
            )
        }
    }

    val day = settings?.let { ScheduleRules.normalize(it.schedule) }?.getOrNull(editDay)
    if (day != null) {
        val dayIndex = editDay
        val dayName = Formats.weekdayFull(DayOfWeek.of(dayIndex + 1), currentLocale())
        ScheduleTimeDialog(
            title = stringResource(
                R.string.time_dialog_title,
                dayName,
                stringResource(if (editEnd) R.string.schedule_end_label else R.string.schedule_start_label),
            ),
            initialMinute = if (editEnd) day.endMinute else day.startMinute,
            isEnd = editEnd,
            validate = { minute -> if (editEnd) ScheduleRules.checkEnd(day, minute) else ScheduleRules.checkStart(day, minute) },
            onConfirm = { minute ->
                if (editEnd) viewModel.setDayEnd(dayIndex, minute) else viewModel.setDayStart(dayIndex, minute)
                editDay = -1
            },
            onDismiss = { editDay = -1 },
        )
    }

    state.local.pendingRestore?.let { pending ->
        RestoreConfirmDialog(pending, onConfirm = viewModel::confirmRestore, onDismiss = viewModel::cancelRestore)
    }
}

/** Öffnet die Dateiauswahl; ohne passende App (sehr selten) eine Meldung statt eines Absturzes. */
private inline fun launchSafely(viewModel: AppViewModel, launch: () -> Unit) {
    try {
        launch()
    } catch (e: ActivityNotFoundException) {
        viewModel.onNoFileApp()
    }
}

@Composable
private fun SendStatus(state: SettingsSendState, onRetry: () -> Unit, onSync: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        when (state) {
            SettingsSendState.Idle -> HintText(stringResource(R.string.send_idle))
            SettingsSendState.Sending -> ProgressText(stringResource(R.string.send_sending))
            SettingsSendState.AwaitingConfirmation -> ProgressText(stringResource(R.string.send_waiting))
            is SettingsSendState.Confirmed -> HintText(stringResource(R.string.send_confirmed))
            SettingsSendState.Unconfirmed -> NoticeCard(
                text = stringResource(R.string.send_unconfirmed),
                isError = false,
                action = { TextButton(onClick = onSync) { Text(stringResource(R.string.sync_now)) } },
            )

            is SettingsSendState.Failed -> NoticeCard(
                text = stringResource(R.string.send_failed, problemText(state.problem)),
                isError = true,
                action = { TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) } },
            )
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ProgressText(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        HintText(text)
    }
}

@Composable
private fun NoSettings(state: AppUiState, onSync: () -> Unit) {
    val text = when {
        state.loading -> stringResource(R.string.loading)
        state.sync.syncing -> stringResource(R.string.empty_syncing)
        else -> state.sync.problem?.let { problemText(it) } ?: stringResource(R.string.settings_empty)
    }
    Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NoticeCard(
            text = text,
            isError = false,
            action = {
                TextButton(onClick = onSync, enabled = !state.sync.syncing && !state.loading) {
                    Text(stringResource(R.string.sync_now))
                }
            },
        )
        HintText(stringResource(R.string.settings_empty_hint))
    }
}

private fun LazyListScope.reminderItems(settings: SyncSettings, viewModel: AppViewModel) {
    item(key = "remindersHeader") { SectionHeader(stringResource(R.string.settings_section_reminders)) }
    item(key = "enabled") {
        SwitchRow(
            title = stringResource(R.string.settings_enabled),
            subtitle = stringResource(if (settings.enabled) R.string.settings_enabled_on else R.string.settings_enabled_off),
            checked = settings.enabled,
            onCheckedChange = viewModel::setEnabled,
        )
    }
    item(key = "interval") {
        StepperRow(
            title = stringResource(R.string.settings_interval),
            subtitle = stringResource(R.string.settings_interval_hint),
            value = stringResource(R.string.minutes_value, settings.intervalMinutes),
            decreaseDescription = stringResource(R.string.cd_interval_decrease),
            increaseDescription = stringResource(R.string.cd_interval_increase),
            canDecrease = SettingsRules.canDecreaseInterval(settings.intervalMinutes),
            canIncrease = SettingsRules.canIncreaseInterval(settings.intervalMinutes),
            onDecrease = { viewModel.stepInterval(-1) },
            onIncrease = { viewModel.stepInterval(1) },
        )
    }
    item(key = "goal") {
        StepperRow(
            title = stringResource(R.string.settings_goal),
            subtitle = stringResource(R.string.settings_goal_hint),
            value = pluralStringResource(R.plurals.breaks_count, settings.dailyGoal, settings.dailyGoal),
            decreaseDescription = stringResource(R.string.cd_goal_decrease),
            increaseDescription = stringResource(R.string.cd_goal_increase),
            canDecrease = SettingsRules.canDecreaseGoal(settings.dailyGoal),
            canIncrease = SettingsRules.canIncreaseGoal(settings.dailyGoal),
            onDecrease = { viewModel.stepDailyGoal(-1) },
            onIncrease = { viewModel.stepDailyGoal(1) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.pauseItems(
    settings: SyncSettings,
    nowMillis: Long,
    zone: ZoneId,
    viewModel: AppViewModel,
) {
    item(key = "pauseHeader") { SectionHeader(stringResource(R.string.settings_section_pause)) }
    item(key = "pause") {
        Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (SettingsRules.isPaused(settings, nowMillis)) {
                Text(text = pauseUntilText(settings.pausedUntilMillis, nowMillis, zone), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = viewModel::endPause) { Text(stringResource(R.string.pause_end)) }
            } else {
                HintText(
                    stringResource(if (settings.enabled) R.string.pause_hint else R.string.pause_hint_disabled),
                )
                // Ausschalten und Pausieren in einer Änderung würde die Uhr ignorieren – und ist ohnehin sinnlos.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::pauseOneHour, enabled = settings.enabled) {
                        Text(stringResource(R.string.pause_one_hour))
                    }
                    OutlinedButton(onClick = viewModel::pauseRestOfDay, enabled = settings.enabled) {
                        Text(stringResource(R.string.pause_rest_of_day))
                    }
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

private fun LazyListScope.scheduleItems(
    settings: SyncSettings,
    onToggle: (Int, Boolean) -> Unit,
    onEdit: (day: Int, end: Boolean) -> Unit,
) {
    val schedule = ScheduleRules.normalize(settings.schedule)
    item(key = "scheduleHeader") { SectionHeader(stringResource(R.string.settings_section_schedule)) }
    item(key = "scheduleHint") {
        Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HintText(stringResource(R.string.schedule_hint))
            ScheduleRules.issues(schedule).forEach { issue ->
                NoticeCard(text = scheduleIssueText(issue), isError = false)
            }
        }
    }
    schedule.forEachIndexed { index, day ->
        item(key = "day$index") {
            DayScheduleRow(
                dayIndex = index,
                day = day,
                onToggle = { onToggle(index, it) },
                onEditStart = { onEdit(index, false) },
                onEditEnd = { onEdit(index, true) },
            )
        }
    }
}

@Composable
private fun DayScheduleRow(
    dayIndex: Int,
    day: SyncDay,
    onToggle: (Boolean) -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
) {
    val name = Formats.weekdayFull(DayOfWeek.of(dayIndex + 1), currentLocale())
    val start = Formats.minuteOfDay(day.startMinute)
    val end = Formats.minuteOfDay(day.endMinute)
    val toggleDescription = stringResource(R.string.cd_day_toggle, name)
    val startDescription = stringResource(R.string.cd_day_start, name, start)
    val endDescription = stringResource(R.string.cd_day_end, name, end)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (day.enabled) {
                TextButton(onClick = onEditStart, modifier = Modifier.semantics { contentDescription = startDescription }) {
                    Text(start)
                }
                Text(text = "–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onEditEnd, modifier = Modifier.semantics { contentDescription = endDescription }) {
                    Text(end)
                }
            } else {
                Text(
                    text = stringResource(R.string.schedule_day_off),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            Switch(
                checked = day.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = toggleDescription },
            )
        }
        HorizontalDivider()
    }
}

private fun LazyListScope.alertItems(settings: SyncSettings, viewModel: AppViewModel) {
    item(key = "alertHeader") { SectionHeader(stringResource(R.string.settings_section_reminder)) }
    item(key = "snooze") {
        SegmentedChoice(
            title = stringResource(R.string.settings_snooze),
            subtitle = stringResource(R.string.settings_snooze_hint),
            options = SettingsRules.SNOOZE_OPTIONS,
            selected = SettingsRules.nearestSnooze(settings.snoozeMinutes),
            label = { it.toString() },
            optionDescription = { stringResource(R.string.minutes_value, it) },
            onSelect = viewModel::setSnooze,
        )
    }
    item(key = "vibration") {
        SegmentedChoice(
            title = stringResource(R.string.settings_vibration),
            options = VibrationLevel.entries,
            selected = VibrationLevel.fromName(settings.vibration),
            label = { vibrationText(it) },
            onSelect = viewModel::setVibration,
        )
    }
    item(key = "quiet") {
        SwitchRow(
            title = stringResource(R.string.settings_quiet),
            subtitle = stringResource(R.string.settings_quiet_hint),
            checked = settings.respectQuietModes,
            onCheckedChange = viewModel::setRespectQuietModes,
        )
    }
    item(key = "worn") {
        SwitchRow(
            title = stringResource(R.string.settings_worn),
            subtitle = stringResource(R.string.settings_worn_hint),
            checked = settings.onlyWhenWorn,
            onCheckedChange = viewModel::setOnlyWhenWorn,
        )
    }
    item(key = "stepsHeader") { SectionHeader(stringResource(R.string.settings_section_steps)) }
    item(key = "autoSteps") {
        SwitchRow(
            title = stringResource(R.string.settings_auto_steps),
            subtitle = stringResource(R.string.settings_auto_steps_hint),
            checked = settings.autoStepEnabled,
            onCheckedChange = viewModel::setAutoSteps,
        )
    }
    item(key = "threshold") {
        SegmentedChoice(
            title = stringResource(R.string.settings_threshold),
            subtitle = stringResource(R.string.settings_threshold_hint),
            options = SettingsRules.STEP_THRESHOLD_OPTIONS,
            selected = SettingsRules.nearestStepThreshold(settings.stepThreshold),
            label = { it.toString() },
            optionDescription = { pluralStringResource(R.plurals.steps_count, it, it) },
            onSelect = viewModel::setStepThreshold,
            enabled = settings.autoStepEnabled,
        )
    }
}

@Composable
private fun DataSection(
    hasData: Boolean,
    restore: RestoreState,
    restoreEnabled: Boolean,
    busy: Boolean,
    onExportCsv: () -> Unit,
    onExportBackup: () -> Unit,
    onRestore: () -> Unit,
    onDismissRestore: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (busy) ProgressText(stringResource(R.string.file_busy))
        DataRow(
            title = stringResource(R.string.data_export_csv),
            subtitle = stringResource(R.string.data_export_csv_hint),
            enabled = hasData,
            onClick = onExportCsv,
        )
        DataRow(
            title = stringResource(R.string.data_export_backup),
            subtitle = stringResource(R.string.data_export_backup_hint),
            enabled = hasData,
            onClick = onExportBackup,
        )
        DataRow(
            title = stringResource(R.string.data_restore),
            subtitle = stringResource(R.string.data_restore_hint),
            enabled = restoreEnabled,
            onClick = onRestore,
        )
        Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            when (restore) {
                RestoreState.Idle -> Unit
                RestoreState.Sending -> ProgressText(stringResource(R.string.restore_sending))
                RestoreState.WaitingForWatch -> ProgressText(stringResource(R.string.restore_waiting))
                is RestoreState.Done -> NoticeCard(
                    text = pluralStringResource(R.plurals.restore_done, restore.restoredEvents, restore.restoredEvents),
                    isError = false,
                    action = { TextButton(onClick = onDismissRestore) { Text(stringResource(R.string.ok)) } },
                )

                is RestoreState.Failed -> NoticeCard(
                    text = stringResource(R.string.restore_failed, problemText(restore.problem)),
                    isError = true,
                    action = { TextButton(onClick = onDismissRestore) { Text(stringResource(R.string.ok)) } },
                )
            }
        }
        if (!hasData && !busy) HintText(stringResource(R.string.data_no_snapshot))
    }
}

@Composable
private fun DataRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )
    }
}

@Composable
private fun RestoreConfirmDialog(pending: PendingRestore, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = { Text(restoreSummaryText(pending.summary)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.restore_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun restoreSummaryText(summary: BackupSummary): String {
    val locale = currentLocale()
    val created = Formats.mediumDate(summary.createdAtMillis.toLocalDate(ZoneId.systemDefault()), locale)
    val first = summary.firstDate
    val last = summary.lastDate
    val content = if (summary.eventCount > 0 && first != null && last != null) {
        pluralStringResource(
            R.plurals.restore_confirm_body,
            summary.eventCount,
            created,
            summary.eventCount,
            Formats.mediumDate(first, locale),
            Formats.mediumDate(last, locale),
        )
    } else {
        stringResource(R.string.restore_confirm_body_empty, created)
    }
    return content + "\n\n" + stringResource(R.string.restore_confirm_warning)
}

private const val DISABLED_ALPHA = 0.38f
