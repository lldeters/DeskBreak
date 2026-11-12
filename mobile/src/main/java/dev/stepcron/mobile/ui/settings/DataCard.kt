package dev.stepcron.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.stepcron.mobile.R
import dev.stepcron.mobile.settings.toLocalDate
import dev.stepcron.mobile.stats.BackupSummary
import dev.stepcron.mobile.sync.RestoreState
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.PendingRestore
import dev.stepcron.mobile.ui.components.AppCard
import dev.stepcron.mobile.ui.currentLocale
import dev.stepcron.mobile.ui.problemText
import dev.stepcron.mobile.ui.theme.LocalChartColors
import java.time.ZoneId

/** Welche Datei-Aktion zuletzt gestartet wurde – an ihr steht während der Arbeit der Kreisel. */
internal enum class FileAction { CSV, BACKUP, RESTORE }

/**
 * Export und Sicherung als Liste: Symbol, was passiert, und – solange eine Datei bearbeitet wird – ein Kreisel an
 * der gestarteten Aktion (die anderen sind so lange gesperrt). Darunter der Stand einer Wiederherstellung.
 */
@Composable
internal fun DataCard(
    hasSnapshot: Boolean,
    fileBusy: Boolean,
    lastAction: FileAction?,
    restore: RestoreState,
    onExportCsv: () -> Unit,
    onExportBackup: () -> Unit,
    onRestore: () -> Unit,
    onDismissRestore: () -> Unit,
) {
    val restoreRunning = restore is RestoreState.Sending || restore is RestoreState.WaitingForWatch
    AppCard(
        title = stringResource(R.string.settings_section_data),
        subtitle = stringResource(if (hasSnapshot) R.string.settings_data_subtitle else R.string.data_no_snapshot),
    ) {
        Column {
            ActionSettingRow(
                icon = R.drawable.ic_table,
                title = stringResource(R.string.data_export_csv),
                subtitle = stringResource(R.string.data_export_csv_hint),
                enabled = hasSnapshot && !fileBusy,
                busy = fileBusy && lastAction == FileAction.CSV,
                onClick = onExportCsv,
            )
            ActionSettingRow(
                icon = R.drawable.ic_download,
                title = stringResource(R.string.data_export_backup),
                subtitle = stringResource(R.string.data_export_backup_hint),
                enabled = hasSnapshot && !fileBusy,
                busy = fileBusy && lastAction == FileAction.BACKUP,
                onClick = onExportBackup,
            )
            ActionSettingRow(
                icon = R.drawable.ic_restore,
                title = stringResource(R.string.data_restore),
                subtitle = stringResource(R.string.data_restore_hint),
                enabled = !fileBusy && !restoreRunning,
                busy = fileBusy && lastAction == FileAction.RESTORE,
                onClick = onRestore,
            )
        }
        // Unbekannt, welche Aktion läuft (z. B. nach dem Neustart der Seite): dann wenigstens sagen, dass etwas läuft.
        if (fileBusy && lastAction == null) {
            InlineStatus(text = stringResource(R.string.file_busy), icon = null, tint = MaterialTheme.colorScheme.primary, progress = true)
        }
        RestoreStatus(restore, onDismissRestore)
    }
}

/** Stand der Wiederherstellung; Ergebnis mit „OK" zum Schließen. TalkBack liest Änderungen vor. */
@Composable
private fun RestoreStatus(restore: RestoreState, onDismiss: () -> Unit) {
    val modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    val ok: @Composable () -> Unit = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } }
    when (restore) {
        RestoreState.Idle -> Unit
        RestoreState.Sending -> InlineStatus(
            text = stringResource(R.string.restore_sending),
            icon = null,
            tint = MaterialTheme.colorScheme.primary,
            progress = true,
            modifier = modifier,
        )

        RestoreState.WaitingForWatch -> InlineStatus(
            text = stringResource(R.string.restore_waiting),
            icon = null,
            tint = MaterialTheme.colorScheme.primary,
            progress = true,
            modifier = modifier,
        )

        is RestoreState.Done -> InlineStatus(
            text = pluralStringResource(R.plurals.restore_done, restore.restoredEvents, restore.restoredEvents),
            icon = R.drawable.ic_check_circle,
            tint = LocalChartColors.current.done,
            action = ok,
            modifier = modifier,
        )

        is RestoreState.Failed -> InlineStatus(
            text = stringResource(R.string.restore_failed, problemText(restore.problem)),
            icon = R.drawable.ic_error,
            tint = MaterialTheme.colorScheme.error,
            action = ok,
            modifier = modifier,
        )
    }
}

/**
 * Rückfrage vor dem Wiederherstellen: was in der Sicherung steckt und dass Einstellungen und Verlauf auf der Uhr
 * ersetzt werden. „Wiederherstellen" in Fehlerfarbe, weil es sich nicht rückgängig machen lässt.
 */
@Composable
internal fun RestoreConfirmDialog(pending: PendingRestore, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_restore), contentDescription = null) },
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = { Text(restoreSummaryText(pending.summary)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.restore_confirm))
            }
        },
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
