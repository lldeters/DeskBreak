package dev.stepcron.wear.ui.exercise

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import dev.stepcron.wear.R
import dev.stepcron.wear.ui.WEAR_SMALL_ROUND
import dev.stepcron.wear.ui.theme.DeskBreakTheme
import kotlinx.coroutines.delay

/**
 * 1-Minuten-Übung: Ring und Restzeit laufen herunter, der Bildschirm bleibt so lange an. Am Ende (oder mit
 * „Fertig") gilt die Pause als gemacht – [onCompleted] wird genau einmal aufgerufen.
 */
@Composable
fun ExerciseScreen(
    exercise: String,
    onCompleted: () -> Unit,
    onBack: () -> Unit,
    durationMillis: Long = DEFAULT_DURATION_MILLIS,
) {
    // Über die Uhrzeit seit dem Start statt über gezählte Ticks: bleibt genau, auch wenn Frames ausfallen,
    // und übersteht eine Konfigurationsänderung.
    val startedAt = rememberSaveable { SystemClock.elapsedRealtime() }
    var completed by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val haptics = LocalHapticFeedback.current

    fun complete() {
        if (completed) return
        completed = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onCompleted()
    }

    LaunchedEffect(completed) {
        while (!completed) {
            now = SystemClock.elapsedRealtime()
            if (now - startedAt >= durationMillis) complete() else delay(TICK_MILLIS)
        }
    }

    // Während der Übung nicht in den Ruhemodus wechseln.
    val view = LocalView.current
    DisposableEffect(view, completed) {
        view.keepScreenOn = !completed
        onDispose { view.keepScreenOn = false }
    }

    val remainingMillis = (durationMillis - (now - startedAt)).coerceIn(0L, durationMillis)
    val remainingSeconds = ((remainingMillis + 999L) / 1000L).toInt()
    val progress = if (completed) 1f else 1f - remainingMillis.toFloat() / durationMillis

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize().padding(4.dp),
            startAngle = 290f,
            endAngle = 250f,
            strokeWidth = 6.dp,
        )
        val timerDescription = pluralStringResource(R.plurals.cd_exercise_timer, remainingSeconds, exercise, remainingSeconds)
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (completed) stringResource(R.string.exercise_done) else exercise,
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
            Spacer(Modifier.height(4.dp))
            if (completed) {
                Text(
                    text = stringResource(R.string.exercise_done_hint),
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                CompactChip(
                    onClick = onBack,
                    label = { Text(stringResource(R.string.exercise_back)) },
                )
            } else {
                Text(
                    text = "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60),
                    style = MaterialTheme.typography.display2,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.semantics { contentDescription = timerDescription },
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = ::complete,
                    modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.exercise_finish),
                        modifier = Modifier.size(ButtonDefaults.SmallIconSize),
                    )
                }
            }
        }
    }
}

private const val DEFAULT_DURATION_MILLIS = 60_000L
private const val TICK_MILLIS = 250L

@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true)
@Composable
private fun ExerciseScreenPreview() {
    DeskBreakTheme {
        Scaffold(timeText = { TimeText() }) {
            ExerciseScreen(exercise = "10 Kniebeugen", onCompleted = {}, onBack = {})
        }
    }
}
