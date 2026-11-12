package dev.stepcron.mobile.ui.license

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.ui.components.AppCard
import dev.stepcron.mobile.ui.components.IconBadge
import dev.stepcron.shared.license.LicenseStatus

/**
 * Kompakter, freundlicher Hinweis während der Testphase („Testphase: noch 2 Tage · Freischalten"). Die ganze Zeile
 * ist antippbar und öffnet das Freischalten; kein zweiter Knopf darin (für TalkBack ein Element).
 */
@Composable
fun TrialBanner(status: LicenseStatus.Trial, nowMillis: Long, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val text = pluralStringResource(R.plurals.license_banner_trial, status.daysLeft, status.daysLeft)
    val action = stringResource(R.string.license_banner_action)
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrialRing(
                status = status,
                nowMillis = nowMillis,
                diameter = 36.dp,
                stroke = 4.dp,
                showUnit = false,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(text = action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Testphase vorbei: deutlich ganz oben, ohne Alarmrot – was sich geändert hat (die Uhr erinnert nicht mehr), was
 * bleibt (der Verlauf) und „Freischalten" als Hauptaktion.
 */
@Composable
fun TrialEndedCard(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AppCard(modifier = modifier, containerColor = colors.tertiaryContainer) {
        Row(modifier = Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            IconBadge(R.drawable.ic_alarm_off, tint = colors.onTertiaryContainer, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.license_ended_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = stringResource(R.string.license_ended_card_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(
            onClick = onUnlock,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(painterResource(R.drawable.ic_lock_open), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.license_unlock))
        }
    }
}
