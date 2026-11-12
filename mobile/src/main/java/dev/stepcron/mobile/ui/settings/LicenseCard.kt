package dev.stepcron.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.stepcron.billing.PlayLinks
import dev.stepcron.mobile.R
import dev.stepcron.mobile.ui.components.AppCard
import dev.stepcron.mobile.ui.license.LicenseUiState
import dev.stepcron.mobile.ui.license.openLink
import dev.stepcron.mobile.ui.license.trialEndsText
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock

/**
 * Lizenz von DeskBreak: eine Zeile, was gerade gilt, und die passenden Aktionen – Freischalten (öffnet die Seite
 * mit den Möglichkeiten) bzw. Details und Abo verwalten (Google Play), dazu Käufe wiederherstellen.
 */
@Composable
internal fun LicenseCard(
    license: LicenseUiState,
    onOpenUnlock: () -> Unit,
    onRestorePurchases: () -> Unit,
    onLinkFailed: () -> Unit,
) {
    val status = license.status ?: return
    val context = LocalContext.current
    val subtitle = when (status) {
        is LicenseStatus.Trial -> pluralStringResource(R.plurals.license_card_trial, status.daysLeft, status.daysLeft) +
            " · " + trialEndsText(status.endsAtMillis, license.nowMillis)

        is LicenseStatus.Expired -> stringResource(R.string.license_card_expired)
        is LicenseStatus.Unlocked -> when (status.unlock) {
            Unlock.LIFETIME -> stringResource(R.string.license_card_lifetime)
            Unlock.SUBSCRIPTION -> stringResource(R.string.license_card_subscription)
        }
    }
    val unlocked = status as? LicenseStatus.Unlocked
    AppCard(title = stringResource(R.string.license_card_title), subtitle = subtitle) {
        Column {
            when (unlocked?.unlock) {
                null -> ActionSettingRow(
                    icon = R.drawable.ic_lock_open,
                    title = stringResource(R.string.license_card_unlock),
                    subtitle = stringResource(R.string.license_card_unlock_hint),
                    enabled = true,
                    busy = false,
                    onClick = onOpenUnlock,
                )

                Unlock.SUBSCRIPTION -> {
                    ActionSettingRow(
                        icon = R.drawable.ic_verified,
                        title = stringResource(R.string.license_card_details),
                        subtitle = stringResource(R.string.license_card_details_hint_subscription),
                        enabled = true,
                        busy = false,
                        onClick = onOpenUnlock,
                    )
                    ActionSettingRow(
                        icon = R.drawable.ic_open_in_new,
                        title = stringResource(R.string.license_manage_subscription),
                        subtitle = stringResource(R.string.license_card_manage_hint),
                        enabled = true,
                        busy = false,
                        onClick = { if (!openLink(context, PlayLinks.manageSubscription(context.packageName))) onLinkFailed() },
                    )
                }

                Unlock.LIFETIME -> ActionSettingRow(
                    icon = R.drawable.ic_verified,
                    title = stringResource(R.string.license_card_details),
                    subtitle = stringResource(R.string.license_card_details_hint),
                    enabled = true,
                    busy = false,
                    onClick = onOpenUnlock,
                )
            }
            if (unlocked?.unlock != Unlock.LIFETIME) {
                ActionSettingRow(
                    icon = R.drawable.ic_restore,
                    title = stringResource(R.string.license_restore),
                    subtitle = stringResource(R.string.license_card_restore_hint),
                    enabled = true,
                    busy = license.restoring,
                    onClick = onRestorePurchases,
                )
            }
        }
    }
}
