package dev.stepcron.mobile.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.stepcron.mobile.R
import dev.stepcron.mobile.sync.SettingsSendState
import dev.stepcron.mobile.ui.problemText
import kotlinx.coroutines.delay

/** So lange bleibt „Auf der Uhr gespeichert" stehen. */
internal const val CONFIRMED_VISIBLE_MILLIS = 3_000L

/** Platz, den die Liste unten frei lässt, damit die Statusanzeige nie Inhalt dauerhaft verdeckt. */
internal val SendStatusSpace = 88.dp

/** Was die Statusanzeige zeigt; `null` = nichts (Ruhe). */
private enum class SendKind { SENDING, WAITING, SAVED, UNCONFIRMED, FAILED }

/**
 * Wo eine Änderung gerade steht – schwebend am unteren Rand, also sichtbar, egal wohin gescrollt ist, und ohne
 * dass sich die Liste unter dem Finger verschiebt. Nur solange etwas passiert: „Wird gesendet …" →
 * „Auf der Uhr gespeichert" (verschwindet nach drei Sekunden); bleibt die Bestätigung aus oder schlägt das Senden
 * fehl, bleibt der Hinweis mit der passenden Aktion stehen. TalkBack liest jeden Wechsel vor.
 */
@Composable
internal fun SendStatus(
    state: SettingsSendState,
    onRetry: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
    clock: () -> Long = System::currentTimeMillis,
) {
    // „Gespeichert" nur kurz – und nicht erneut, wenn man später auf die Seite zurückkommt.
    var savedVisible by remember(state) {
        mutableStateOf(state is SettingsSendState.Confirmed && clock() - state.atMillis < CONFIRMED_VISIBLE_MILLIS)
    }
    if (state is SettingsSendState.Confirmed && savedVisible) {
        LaunchedEffect(state) {
            delay((CONFIRMED_VISIBLE_MILLIS - (clock() - state.atMillis)).coerceAtLeast(0L))
            savedVisible = false
        }
    }
    val kind = when (state) {
        SettingsSendState.Idle -> null
        SettingsSendState.Sending -> SendKind.SENDING
        SettingsSendState.AwaitingConfirmation -> SendKind.WAITING
        is SettingsSendState.Confirmed -> SendKind.SAVED.takeIf { savedVisible }
        SettingsSendState.Unconfirmed -> SendKind.UNCONFIRMED
        is SettingsSendState.Failed -> SendKind.FAILED
    }
    // Beim Ausblenden den letzten Inhalt weiter zeigen, statt leer zu verschwinden (kein State: ändert sich nur
    // zusammen mit state).
    val last = remember { arrayOfNulls<Pair<SendKind, SettingsSendState>>(1) }
    if (kind != null) last[0] = kind to state
    val shown = last[0]

    AnimatedVisibility(
        visible = kind != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = shown,
            contentKey = { it?.first },
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
            label = "sendStatus",
        ) { target ->
            if (target != null) StatusSurface(target.first, target.second, onRetry, onSync)
        }
    }
}

@Composable
private fun StatusSurface(kind: SendKind, state: SettingsSendState, onRetry: () -> Unit, onSync: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val failed = kind == SendKind.FAILED
    val container = if (failed) colors.errorContainer else colors.inverseSurface
    val content = if (failed) colors.onErrorContainer else colors.inverseOnSurface
    val accent = if (failed) colors.error else colors.inversePrimary
    val text = when (kind) {
        SendKind.SENDING -> stringResource(R.string.send_sending)
        SendKind.WAITING -> stringResource(R.string.send_waiting)
        SendKind.SAVED -> stringResource(R.string.settings_send_saved)
        SendKind.UNCONFIRMED -> stringResource(R.string.settings_send_unconfirmed)
        SendKind.FAILED -> stringResource(
            R.string.send_failed,
            (state as? SettingsSendState.Failed)?.let { problemText(it.problem) }.orEmpty(),
        )
    }
    val action: Pair<String, () -> Unit>? = when (kind) {
        SendKind.UNCONFIRMED -> stringResource(R.string.sync_now) to onSync
        SendKind.FAILED -> stringResource(R.string.retry) to onRetry
        else -> null
    }
    Surface(
        shape = if (action == null) CircleShape else MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(max = 560.dp),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = if (action != null) 8.dp else 20.dp, top = 8.dp, bottom = 8.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusIcon(kind, accent)
            Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            if (action != null) {
                TextButton(
                    onClick = action.second,
                    colors = ButtonDefaults.textButtonColors(contentColor = if (failed) colors.error else colors.inversePrimary),
                ) {
                    Text(action.first)
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(kind: SendKind, accent: Color) {
    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        when (kind) {
            SendKind.SENDING, SendKind.WAITING ->
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)

            SendKind.SAVED -> Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = accent)
            SendKind.UNCONFIRMED -> Icon(painterResource(R.drawable.ic_watch), contentDescription = null, tint = accent)
            SendKind.FAILED -> Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = accent)
        }
    }
}
