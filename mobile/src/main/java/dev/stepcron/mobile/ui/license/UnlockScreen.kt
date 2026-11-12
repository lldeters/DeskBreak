package dev.stepcron.mobile.ui.license

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stepcron.billing.BillingAvailability
import dev.stepcron.billing.BillingOffer
import dev.stepcron.billing.BillingProblem
import dev.stepcron.billing.BillingState
import dev.stepcron.billing.PlayLinks
import dev.stepcron.mobile.R
import dev.stepcron.mobile.ui.components.AppCard
import dev.stepcron.mobile.ui.components.IconBadge
import dev.stepcron.mobile.ui.theme.LocalChartColors
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock

/**
 * Freischalten: oben der Stand (Testphase mit Ring, beendet oder freigeschaltet), darunter – solange nicht
 * freigeschaltet – was DeskBreak bietet, die zwei Möglichkeiten als Auswahl (Einmalkauf vorgewählt), eine
 * Hauptaktion mit Preis und direkt darunter, was sie genau bedeutet. Preise kommen immer von Google Play. Vor dem
 * Kauf steht klar da: Preis, Zeitraum, automatische Verlängerung des Abos und wie man kündigt – und während der
 * Testphase, dass ein Abo sofort abgerechnet wird (Play kennt keinen Probezeitraum, die Testphase ist die der App).
 */
@Composable
fun UnlockScreen(state: LicenseUiState, actions: LicenseActions, modifier: Modifier = Modifier) {
    val status = state.status
    if (status == null) {
        val description = stringResource(R.string.loading)
        Box(modifier = modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = description })
        }
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val unlocked = status as? LicenseStatus.Unlocked
        StatusCard(status, state.nowMillis, showBenefits = unlocked == null)
        when (unlocked?.unlock) {
            null -> PurchaseContent(state, actions)
            Unlock.LIFETIME -> IncludedCard()
            Unlock.SUBSCRIPTION -> SubscriptionContent(state, actions)
        }
    }
}

// --- Stand ---

/** Ring bzw. Symbol, eine Überschrift und ein Satz dazu; TalkBack liest Änderungen (z. B. nach dem Kauf) vor. */
@Composable
private fun StatusCard(status: LicenseStatus, nowMillis: Long, showBenefits: Boolean) {
    val colors = MaterialTheme.colorScheme
    val chart = LocalChartColors.current
    val expired = status is LicenseStatus.Expired
    AppCard(containerColor = if (expired) colors.tertiaryContainer else colors.secondaryContainer) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (status) {
                is LicenseStatus.Trial -> {
                    val description = pluralStringResource(R.plurals.cd_trial_ring, status.daysLeft, status.daysLeft)
                    TrialRing(status, nowMillis, modifier = Modifier.clearAndSetSemantics { contentDescription = description })
                }

                is LicenseStatus.Expired -> IconBadge(R.drawable.ic_alarm_off, tint = colors.onTertiaryContainer, size = 88.dp)
                is LicenseStatus.Unlocked -> IconBadge(R.drawable.ic_verified, tint = chart.done, size = 88.dp)
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val texts = statusTexts(status, nowMillis)
                Text(
                    text = texts.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (expired) colors.onTertiaryContainer else colors.onSurfaceVariant,
                )
                Text(text = texts.headline, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(text = texts.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (showBenefits) {
            HorizontalDivider(color = if (expired) colors.onTertiaryContainer.copy(alpha = 0.16f) else colors.outlineVariant)
            // Auf der getönten Fläche „Testphase beendet" in deren Schriftfarbe statt Grün.
            Benefits(tint = if (expired) colors.onTertiaryContainer else colors.primary)
        }
    }
}

private data class StatusTexts(val label: String, val headline: String, val detail: String)

@Composable
private fun statusTexts(status: LicenseStatus, nowMillis: Long): StatusTexts = when (status) {
    is LicenseStatus.Trial -> StatusTexts(
        stringResource(R.string.license_trial_label),
        pluralStringResource(R.plurals.license_trial_days_left, status.daysLeft, status.daysLeft),
        trialEndsText(status.endsAtMillis, nowMillis),
    )

    is LicenseStatus.Expired -> StatusTexts(
        stringResource(R.string.license_expired_label),
        stringResource(R.string.license_expired_headline),
        stringResource(R.string.license_expired_detail),
    )

    is LicenseStatus.Unlocked -> when (status.unlock) {
        Unlock.LIFETIME -> StatusTexts(
            stringResource(R.string.license_lifetime_label),
            stringResource(R.string.license_lifetime_headline),
            stringResource(R.string.license_lifetime_detail),
        )

        Unlock.SUBSCRIPTION -> StatusTexts(
            stringResource(R.string.license_subscription_label),
            stringResource(R.string.license_subscription_headline),
            stringResource(R.string.license_subscription_detail),
        )
    }
}

/** Drei kurze Zeilen: was die Freischaltung bringt. */
@Composable
private fun Benefits(tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BenefitRow(R.drawable.ic_alarm, stringResource(R.string.license_benefit_reminders), tint)
        BenefitRow(R.drawable.ic_watch, stringResource(R.string.license_benefit_devices), tint)
        BenefitRow(R.drawable.ic_favorite, stringResource(R.string.license_benefit_support), tint)
    }
}

@Composable
private fun BenefitRow(@DrawableRes icon: Int, text: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, tint = tint, size = 32.dp)
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

// --- Kaufen ---

@Composable
private fun PurchaseContent(state: LicenseUiState, actions: LicenseActions) {
    val billing = state.billing
    val context = LocalContext.current
    // Einmalkauf vorgewählt; fehlt ein Angebot bei Play, gilt das andere.
    var chosen by rememberSaveable { mutableStateOf(Unlock.LIFETIME) }
    val selected = when {
        chosen in billing.offers -> chosen
        else -> OPTION_ORDER.firstOrNull { it in billing.offers } ?: chosen
    }

    if (billing.pending.isNotEmpty()) PendingNotice()

    SectionTitle(stringResource(R.string.license_choose_title))
    when {
        billing.availability == BillingAvailability.CONNECTING -> OptionSkeleton()
        billing.availability == BillingAvailability.UNAVAILABLE -> Notice(
            title = stringResource(R.string.license_unavailable_title),
            body = stringResource(R.string.license_unavailable_body),
            onRetry = actions::retryBilling,
        )

        billing.availability == BillingAvailability.ERROR -> Notice(
            title = stringResource(R.string.license_error_title),
            body = stringResource(errorBody(billing.problem)),
            onRetry = actions::retryBilling,
        )

        billing.offers.isEmpty() -> Notice(
            title = stringResource(R.string.license_products_missing_title),
            body = stringResource(R.string.license_products_missing_body),
            onRetry = actions::retryBilling,
        )

        else -> {
            Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OPTION_ORDER.forEach { unlock ->
                    val offer = billing.offers[unlock] ?: return@forEach
                    OptionCard(offer, selected = unlock == selected, onSelect = { chosen = unlock })
                }
            }
            val offer = billing.offers.getValue(selected)
            PurchaseButton(
                offer = offer,
                pending = selected in billing.pending,
                billingStartsToday = state.status is LicenseStatus.Trial && selected == Unlock.SUBSCRIPTION,
                onClick = { context.findActivity()?.let { actions.purchase(it, selected) } },
            )
        }
    }
    if (billing.availability == BillingAvailability.READY) {
        RestoreButton(restoring = state.restoring, onClick = actions::restorePurchases)
    }
    FinePrint(showFreeNote = true, showManageLink = true, onLinkFailed = actions::onLinkFailed)
}

private val OPTION_ORDER = listOf(Unlock.LIFETIME, Unlock.SUBSCRIPTION)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = 8.dp, start = 4.dp)
            .semantics { heading() },
    )
}

/**
 * Eine Möglichkeit als Auswahlkarte (Optionsfeld für TalkBack): links Name und Kurzbeschreibung, rechts der Preis
 * von Play und darunter „einmalig" bzw. „pro Monat". Gewählt: kräftiger Rahmen, getönte Fläche, gefüllter Punkt.
 */
@Composable
private fun OptionCard(offer: BillingOffer, selected: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val border by animateColorAsState(if (selected) colors.primary else colors.outlineVariant, label = "border")
    val container by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainerLow, label = "container")
    val shape = MaterialTheme.shapes.medium
    val (title, hint, unit) = when (offer.unlock) {
        Unlock.LIFETIME -> Triple(
            stringResource(R.string.license_option_lifetime),
            stringResource(R.string.license_option_lifetime_hint),
            stringResource(R.string.license_option_lifetime_period),
        )

        Unlock.SUBSCRIPTION -> Triple(
            stringResource(R.string.license_option_monthly),
            stringResource(R.string.license_option_monthly_hint),
            periodText(offer.billingPeriod),
        )
    }
    Surface(
        shape = shape,
        color = container,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(start = 8.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(text = offer.formattedPrice, style = MaterialTheme.typography.titleLarge)
                Text(text = unit, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Platzhalter in Größe der Auswahlkarten, solange Play die Angebote lädt (leicht pulsierend). */
@Composable
private fun OptionSkeleton() {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse",
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = pulse)
    val connecting = stringResource(R.string.license_connecting)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier.clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(2) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 72.dp)
                            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(20.dp).background(block, CircleShape))
                        Spacer(Modifier.width(24.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth(0.55f).height(14.dp).background(block, MaterialTheme.shapes.small))
                            Box(Modifier.fillMaxWidth(0.8f).height(10.dp).background(block, MaterialTheme.shapes.small))
                        }
                        Spacer(Modifier.width(24.dp))
                        Box(Modifier.size(width = 56.dp, height = 20.dp).background(block, MaterialTheme.shapes.small))
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(text = connecting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Hauptaktion mit Preis („Für 5,00 € freischalten") und direkt darunter in einem Satz, was sie bedeutet – beim Abo
 * mit automatischer Verlängerung und Kündigung. [billingStartsToday]: Abo während der Testphase – die erste Zahlung
 * ist heute fällig, nicht erst nach der Testphase; das steht als eigene, etwas kräftigere Zeile darunter. Wartet eine
 * Zahlung für diese Möglichkeit, ist sie gesperrt.
 */
@Composable
private fun PurchaseButton(offer: BillingOffer, pending: Boolean, billingStartsToday: Boolean, onClick: () -> Unit) {
    val price = priceText(offer)
    val label = when {
        pending -> stringResource(R.string.license_pending_button)
        offer.unlock == Unlock.LIFETIME -> stringResource(R.string.license_buy_lifetime, price)
        else -> stringResource(R.string.license_buy_subscription, price)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onClick,
            enabled = !pending,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(painterResource(R.drawable.ic_lock_open), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
        Text(
            text = when (offer.unlock) {
                Unlock.LIFETIME -> stringResource(R.string.license_summary_lifetime, price)
                Unlock.SUBSCRIPTION -> stringResource(R.string.license_summary_subscription, price)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
        if (billingStartsToday) {
            Text(
                text = stringResource(R.string.license_trial_billing_today),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun RestoreButton(restoring: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = !restoring,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (restoring) {
            CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(stringResource(if (restoring) R.string.license_restoring else R.string.license_restore))
    }
}

/** Zahlung begonnen, aber noch nicht bestätigt (z. B. Barzahlung im Laden). */
@Composable
private fun PendingNotice() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_schedule), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text = stringResource(R.string.license_pending), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Erklärung, warum gerade nicht gekauft werden kann, mit „Erneut versuchen". */
@Composable
private fun Notice(title: String, body: String, onRetry: () -> Unit) {
    AppCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.Top,
        ) {
            IconBadge(R.drawable.ic_error, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(painterResource(R.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.license_retry))
        }
    }
}

private fun errorBody(problem: BillingProblem?): Int = when (problem) {
    BillingProblem.NETWORK -> R.string.license_error_network
    BillingProblem.SERVICE_UNAVAILABLE -> R.string.license_error_service
    else -> R.string.license_error_generic
}

/**
 * Kleingedrucktes für alle Fälle: Zahlung über Google Play, automatische Verlängerung des Abos und wie man kündigt
 * (mit Link zur Abo-Verwaltung von Play).
 */
@Composable
private fun FinePrint(showFreeNote: Boolean, showManageLink: Boolean, onLinkFailed: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showFreeNote) {
            FinePrintText(stringResource(R.string.license_free_note))
        }
        FinePrintText(stringResource(R.string.license_fine_print))
        if (showManageLink) {
            TextButton(
                onClick = { if (!openLink(context, PlayLinks.manageSubscription(context.packageName))) onLinkFailed() },
                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.license_manage_link), textAlign = TextAlign.Center)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
        }
    }
}

@Composable
private fun FinePrintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// --- Freigeschaltet ---

/** Was mit der Freischaltung dazugehört – zum Nachlesen, ohne Kaufaufforderung. */
@Composable
private fun IncludedCard() {
    val done = LocalChartColors.current.done
    AppCard(title = stringResource(R.string.license_included_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BenefitRow(R.drawable.ic_check, stringResource(R.string.license_included_reminders), tint = done)
            BenefitRow(R.drawable.ic_check, stringResource(R.string.license_included_devices), tint = done)
            BenefitRow(R.drawable.ic_check, stringResource(R.string.license_included_data), tint = done)
        }
    }
}

/**
 * Abo aktiv: Abo verwalten (Google Play) und – ehrlich – wie man auf den Einmalkauf wechselt: kaufen und danach das
 * Abo in Play kündigen (das macht DeskBreak nicht selbst).
 */
@Composable
private fun SubscriptionContent(state: LicenseUiState, actions: LicenseActions) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = { if (!openLink(context, PlayLinks.manageSubscription(context.packageName))) actions.onLinkFailed() },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.license_manage_subscription))
    }
    IncludedCard()
    val lifetime = state.billing.lifetimeOffer()
    if (lifetime != null) {
        AppCard(
            title = stringResource(R.string.license_switch_title),
            subtitle = stringResource(R.string.license_switch_body),
            icon = R.drawable.ic_lock_open,
        ) {
            val pending = Unlock.LIFETIME in state.billing.pending
            OutlinedButton(
                onClick = { context.findActivity()?.let { actions.purchase(it, Unlock.LIFETIME) } },
                enabled = !pending,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (pending) {
                        stringResource(R.string.license_pending_button)
                    } else {
                        stringResource(R.string.license_switch_button, lifetime.formattedPrice)
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    // Abo verwalten steht schon oben als eigene Aktion.
    FinePrint(showFreeNote = false, showManageLink = false, onLinkFailed = actions::onLinkFailed)
}

/** Angebot für den Einmalkauf, sofern Play verbunden ist und es kennt. */
private fun BillingState.lifetimeOffer(): BillingOffer? =
    offers[Unlock.LIFETIME]?.takeIf { availability == BillingAvailability.READY }
