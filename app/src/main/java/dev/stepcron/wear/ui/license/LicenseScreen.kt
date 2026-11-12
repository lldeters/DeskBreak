package dev.stepcron.wear.ui.license

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import dev.stepcron.billing.BillingOffer
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import dev.stepcron.wear.R
import dev.stepcron.wear.ui.WEAR_SMALL_ROUND
import dev.stepcron.wear.ui.theme.DeskBreakTheme

/**
 * Freischaltung: Stand der Lizenz, „Am Handy freischalten" (öffnet Google Play auf dem Handy), Kauf direkt auf der Uhr,
 * wenn Play Billing hier bereit ist, und „Käufe prüfen". Freigeschaltet ein Dank, beim Abo die Abo-Verwaltung.
 */
@Composable
fun LicenseScreen(
    state: LicenseUiState,
    onUnlockOnPhone: () -> Unit,
    onBuy: (Unlock) -> Unit,
    onCheckPurchases: () -> Unit,
    onManageSubscription: () -> Unit,
) {
    // Wie üblich auf Wear OS steht der Stand (Eintrag 1) in der Mitte, der Titel darüber – so ist die erste Aktion
    // schon zu sehen.
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 1)
    Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 1),
        ) {
            item(key = "header") {
                ListHeader { Text(stringResource(R.string.license_title), color = MaterialTheme.colors.primary) }
            }
            val status = state.status ?: return@ScalingLazyColumn
            item(key = "status") { LicenseStatusText(status) }

            when (state.unlock) {
                null -> unlockItems(state, onUnlockOnPhone, onBuy)
                Unlock.SUBSCRIPTION -> {
                    item(key = "manage") {
                        Chip(
                            onClick = onManageSubscription,
                            label = { Text(stringResource(R.string.license_manage_subscription), maxLines = 1) },
                            secondaryLabel = {
                                PhoneLinkText(state.phoneLink, R.string.license_manage_subscription_secondary)
                            },
                            icon = { ChipIcon(R.drawable.ic_phone) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Unlock.LIFETIME -> Unit
            }

            item(key = "check") {
                Chip(
                    onClick = onCheckPurchases,
                    label = { Text(stringResource(R.string.license_check), maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            stringResource(if (state.checking) R.string.license_checking else R.string.license_check_secondary),
                            maxLines = 2,
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.unlock != Unlock.LIFETIME) {
                item(key = "cancel_anytime") { Note(stringResource(R.string.license_cancel_anytime)) }
            }
        }
    }
}

/** Noch nicht freigeschaltet: über das Handy oder direkt auf der Uhr. */
private fun ScalingLazyListScope.unlockItems(
    state: LicenseUiState,
    onUnlockOnPhone: () -> Unit,
    onBuy: (Unlock) -> Unit,
) {
    item(key = "unlock_on_phone") {
        Chip(
            onClick = onUnlockOnPhone,
            label = { Text(stringResource(R.string.license_unlock_on_phone), maxLines = 2) },
            secondaryLabel = { PhoneLinkText(state.phoneLink, R.string.license_unlock_on_phone_secondary) },
            icon = { ChipIcon(R.drawable.ic_phone) },
            colors = ChipDefaults.primaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val notice = state.notice
    if (notice != null) {
        item(key = "notice") {
            Text(
                text = stringResource(noticeText(notice)),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    if (state.offers.isEmpty()) return
    item(key = "buy_header") { ListHeader { Text(stringResource(R.string.license_buy_header)) } }
    state.offers.forEach { offer ->
        item(key = "buy_${offer.unlock.name}") {
            Chip(
                onClick = { onBuy(offer.unlock) },
                label = { Text(offerLabel(offer), maxLines = 1) },
                secondaryLabel = { Text(stringResource(offerSecondary(offer.unlock)), maxLines = 1) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LicenseStatusText(status: LicenseStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (status) {
            is LicenseStatus.Trial -> {
                Headline(stringResource(R.string.license_status_trial))
                Caption(pluralStringResource(R.plurals.license_status_trial_days, status.daysLeft, status.daysLeft))
                Spacer(Modifier.height(2.dp))
                Caption(stringResource(R.string.license_status_trial_hint))
            }

            is LicenseStatus.Expired -> {
                Headline(stringResource(R.string.license_status_expired))
                Caption(stringResource(R.string.license_status_expired_hint))
            }

            is LicenseStatus.Unlocked -> {
                Headline(stringResource(R.string.license_status_thanks))
                Caption(
                    stringResource(
                        when (status.unlock) {
                            Unlock.LIFETIME -> R.string.license_status_lifetime
                            Unlock.SUBSCRIPTION -> R.string.license_status_subscription
                        },
                    ),
                )
            }
        }
    }
}

/** Zweite Zeile der Handy-Aktionen: der Stand des Öffnens, sonst [idleText]. */
@Composable
private fun PhoneLinkText(phoneLink: PhoneLinkState, @StringRes idleText: Int) {
    val text = when (phoneLink) {
        PhoneLinkState.IDLE -> idleText
        PhoneLinkState.OPENING -> R.string.license_phone_opening
        PhoneLinkState.OPENED -> R.string.license_phone_opened
        PhoneLinkState.FAILED -> R.string.license_phone_failed
    }
    // Das Ergebnis ansagen, es ändert nur diese Zeile.
    Text(stringResource(text), maxLines = 2, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun offerLabel(offer: BillingOffer): String = when (offer.unlock) {
    Unlock.LIFETIME -> stringResource(R.string.license_buy_lifetime, offer.formattedPrice)
    Unlock.SUBSCRIPTION -> stringResource(R.string.license_buy_monthly, offer.formattedPrice)
}

@StringRes
private fun offerSecondary(unlock: Unlock): Int = when (unlock) {
    Unlock.LIFETIME -> R.string.license_buy_lifetime_secondary
    Unlock.SUBSCRIPTION -> R.string.license_buy_monthly_secondary
}

@StringRes
private fun noticeText(notice: LicenseNotice): Int = when (notice) {
    LicenseNotice.PENDING -> R.string.license_notice_pending
    LicenseNotice.UNREACHABLE -> R.string.license_notice_unreachable
    LicenseNotice.FAILED -> R.string.license_notice_failed
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
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption3,
        color = MaterialTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChipIcon(@DrawableRes iconRes: Int) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(ChipDefaults.IconSize),
    )
}

@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true)
@Composable
private fun LicenseScreenTrialPreview() {
    DeskBreakTheme {
        Scaffold(timeText = { TimeText() }) {
            LicenseScreen(
                state = LicenseUiState(
                    loading = false,
                    status = LicenseStatus.Trial(endsAtMillis = 0L, daysLeft = 2),
                    offers = listOf(
                        BillingOffer(Unlock.LIFETIME, "5,00 €"),
                        BillingOffer(Unlock.SUBSCRIPTION, "1,00 €", "P1M"),
                    ),
                ),
                onUnlockOnPhone = {},
                onBuy = {},
                onCheckPurchases = {},
                onManageSubscription = {},
            )
        }
    }
}

@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true)
@Composable
private fun LicenseScreenUnlockedPreview() {
    DeskBreakTheme {
        Scaffold(timeText = { TimeText() }) {
            LicenseScreen(
                state = LicenseUiState(loading = false, status = LicenseStatus.Unlocked(Unlock.SUBSCRIPTION)),
                onUnlockOnPhone = {},
                onBuy = {},
                onCheckPurchases = {},
                onManageSubscription = {},
            )
        }
    }
}
