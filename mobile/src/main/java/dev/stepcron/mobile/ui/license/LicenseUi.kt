package dev.stepcron.mobile.ui.license

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stepcron.billing.BillingOffer
import dev.stepcron.billing.BillingState
import dev.stepcron.mobile.R
import dev.stepcron.mobile.settings.toLocalDate
import dev.stepcron.mobile.ui.Formats
import dev.stepcron.mobile.ui.components.ProgressRing
import dev.stepcron.mobile.ui.currentLocale
import dev.stepcron.mobile.ui.theme.LocalChartColors
import dev.stepcron.shared.license.LicenseRules
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock
import java.time.ZoneId

/** Lizenz für die Oberfläche: Stand (mit laufender Uhr berechnet) und Verbindung zu Google Play. */
data class LicenseUiState(
    /** `null`, solange der gespeicherte Stand noch nicht gelesen ist – dann zeigt die Oberfläche nichts dazu an. */
    val status: LicenseStatus? = null,
    val billing: BillingState = BillingState(),
    /** „Käufe wiederherstellen" läuft. */
    val restoring: Boolean = false,
    val nowMillis: Long = 0L,
)

/** Was die Lizenz-Oberfläche auslösen kann; das ViewModel setzt es um, Screenshots kommen ohne aus. */
interface LicenseActions {
    /** Öffnet den Kaufdialog von Google Play für [unlock]. */
    fun purchase(activity: Activity, unlock: Unlock)

    /** Käufe neu von Google Play einlesen; das Ergebnis erscheint als Kurzmeldung. */
    fun restorePurchases()

    /** Verbindung zu Google Play erneut versuchen. */
    fun retryBilling()

    /** Ein Link zu Google Play ließ sich nicht öffnen (keine passende App). */
    fun onLinkFailed()
}

/** Tut nichts – für Vorschauen und Screenshots. */
object NoLicenseActions : LicenseActions {
    override fun purchase(activity: Activity, unlock: Unlock) = Unit
    override fun restorePurchases() = Unit
    override fun retryBilling() = Unit
    override fun onLinkFailed() = Unit
}

/** Die Activity hinter [this] (für den Kaufdialog von Play), falls es eine gibt. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Öffnet [uri] (Google Play bzw. Browser); `false`, wenn keine App das kann. */
internal fun openLink(context: Context, uri: Uri): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    true
} catch (e: ActivityNotFoundException) {
    false
}

/** Anteil der Testphase, der noch bleibt (für den Ring). */
internal fun LicenseStatus.Trial.remainingFraction(nowMillis: Long): Float =
    ((endsAtMillis - nowMillis).toFloat() / LicenseRules.TRIAL_MILLIS).coerceIn(0f, 1f)

/** „Endet heute um 10:22", „… morgen um …" oder „… am Do., 13. Nov. um …" – in der Zeitzone des Handys. */
@Composable
internal fun trialEndsText(endsAtMillis: Long, nowMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = endsAtMillis.toLocalDate(zone)
    val today = nowMillis.toLocalDate(zone)
    val time = Formats.clock(endsAtMillis, zone)
    return when (date) {
        today -> stringResource(R.string.license_trial_ends_today, time)
        today.plusDays(1) -> stringResource(R.string.license_trial_ends_tomorrow, time)
        else -> stringResource(R.string.license_trial_ends_on, Formats.shortDate(date, currentLocale()), time)
    }
}

/**
 * Abrechnungszeitraum eines Abos laut Play (ISO 8601, z. B. „P1M") als „pro Monat", „alle 3 Monate" usw. Ohne
 * Angabe gilt der Monat – das Abo ist in der Play Console als Monatsabo angelegt.
 */
@Composable
internal fun periodText(billingPeriod: String?): String {
    val match = billingPeriod?.let { PERIOD.matchEntire(it) }
    val count = match?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    return when (match?.groupValues?.get(2)) {
        "W" -> pluralStringResource(R.plurals.license_period_week, count, count)
        "Y" -> pluralStringResource(R.plurals.license_period_year, count, count)
        "D" -> if (count % 7 == 0) {
            pluralStringResource(R.plurals.license_period_week, count / 7, count / 7)
        } else {
            pluralStringResource(R.plurals.license_period_day, count, count)
        }

        else -> pluralStringResource(R.plurals.license_period_month, count, count)
    }
}

private val PERIOD = Regex("P(\\d+)([DWMY])")

/** Preis eines Angebots, beim Abo mit Zeitraum: „5,00 €" bzw. „1,00 € pro Monat". */
@Composable
internal fun priceText(offer: BillingOffer): String = when (offer.unlock) {
    Unlock.LIFETIME -> offer.formattedPrice
    Unlock.SUBSCRIPTION -> stringResource(R.string.license_price_with_period, offer.formattedPrice, periodText(offer.billingPeriod))
}

/** Kleiner Ring mit den verbleibenden Tagen der Testphase in der Mitte (Beschreibung setzt der Aufrufer). */
@Composable
internal fun TrialRing(
    status: LicenseStatus.Trial,
    nowMillis: Long,
    modifier: Modifier = Modifier,
    diameter: Dp = 88.dp,
    stroke: Dp = 8.dp,
    showUnit: Boolean = true,
) {
    val colors = LocalChartColors.current
    ProgressRing(
        progress = status.remainingFraction(nowMillis),
        color = colors.done,
        trackColor = colors.track,
        modifier = modifier,
        diameter = diameter,
        stroke = stroke,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = status.daysLeft.toString(),
                    style = if (showUnit) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelLarge,
                )
                if (showUnit) {
                    Text(
                        text = pluralStringResource(R.plurals.license_ring_days, status.daysLeft),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
