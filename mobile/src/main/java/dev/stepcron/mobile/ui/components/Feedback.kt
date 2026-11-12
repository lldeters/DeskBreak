package dev.stepcron.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.sync.SyncProblem
import dev.stepcron.mobile.ui.isError
import dev.stepcron.mobile.ui.problemText

/**
 * Hinweis über ein Problem beim Abgleich – nur solange es besteht, mit „Erneut versuchen". Fehler rot, Hinweise
 * (z. B. Uhr gerade nicht erreichbar) neutral; TalkBack liest Änderungen vor.
 */
@Composable
fun ProblemBanner(problem: SyncProblem, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val error = problem.isError()
    val container = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(if (error) R.drawable.ic_alarm_off else R.drawable.ic_watch), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text = problemText(problem), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

/**
 * Noch kein Stand der Uhr: erklärt in drei Schritten, wie die Verbindung zustande kommt, und bietet den Abgleich
 * als Hauptaktion an. Ein bekanntes Problem steht als konkreter Hinweis darüber.
 */
@Composable
fun ConnectWatchCard(problem: SyncProblem?, syncing: Boolean, onSyncNow: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(R.drawable.ic_watch, size = 72.dp)
            Text(
                text = stringResource(R.string.connect_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.connect_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (problem != null && !syncing) ProblemBanner(problem, onRetry = onSyncNow)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Step(1, stringResource(R.string.connect_step_install))
            Step(2, stringResource(R.string.connect_step_open))
            Step(3, stringResource(R.string.connect_step_sync))
        }
        Button(
            onClick = onSyncNow,
            enabled = !syncing,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            if (syncing) {
                CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(R.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(if (syncing) R.string.empty_syncing else R.string.sync_now))
        }
    }
}

/** Nummerierter Schritt einer Anleitung. */
@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Text(text = number.toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}
