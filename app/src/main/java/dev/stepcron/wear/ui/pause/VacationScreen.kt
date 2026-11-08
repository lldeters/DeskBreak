package dev.stepcron.wear.ui.pause

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberPickerState
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.PauseOptions
import dev.stepcron.wear.ui.WEAR_SMALL_ROUND
import dev.stepcron.wear.ui.components.AccessiblePicker
import dev.stepcron.wear.ui.shortDate
import dev.stepcron.wear.ui.theme.DeskBreakTheme
import java.time.LocalDate

/**
 * Urlaub: den letzten freien Tag mit der Krone bzw. Lünette wählen und mit dem Haken bestätigen. Danach geht
 * es am ersten Erinnerungstag mit einem frischen Zyklus weiter. Zurückwischen bricht ab.
 */
@Composable
fun VacationScreen(
    today: LocalDate,
    onConfirm: (lastDay: LocalDate) -> Unit,
) {
    val days = remember(today) { PauseOptions.vacationDays(today) }
    val state = rememberPickerState(
        initialNumberOfOptions = days.size,
        initiallySelectedOption = INITIAL_DAYS - 1,
        repeatItems = false,
    )
    val configuration = LocalConfiguration.current
    val resources = LocalContext.current.resources
    val labels = remember(days, configuration, resources) { days.map { shortDate(resources, it) } }
    val selected by remember(days, state) { derivedStateOf { state.selectedOption.coerceIn(days.indices) } }
    val title = stringResource(R.string.vacation_title)
    val description = stringResource(R.string.cd_time_picker, title, labels[selected])

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(2.dp))
            AccessiblePicker(
                state = state,
                previousLabel = stringResource(R.string.cd_picker_earlier_day),
                nextLabel = stringResource(R.string.cd_picker_later_day),
            ) {
                Picker(
                    state = state,
                    contentDescription = description,
                    modifier = Modifier.size(width = 150.dp, height = 88.dp),
                    separation = 4.dp,
                ) { optionIndex ->
                    Text(
                        text = labels[optionIndex],
                        style = MaterialTheme.typography.title2,
                        color = if (optionIndex == selectedOption) {
                            MaterialTheme.colors.onSurface
                        } else {
                            MaterialTheme.colors.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            CompactButton(onClick = { onConfirm(days[selected]) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.vacation_confirm),
                    modifier = Modifier.size(ButtonDefaults.SmallIconSize),
                )
            }
        }
    }
}

/** Vorauswahl: eine Woche. */
private const val INITIAL_DAYS = 7

@Preview(device = WEAR_SMALL_ROUND, showSystemUi = true)
@Composable
private fun VacationScreenPreview() {
    DeskBreakTheme {
        Scaffold(timeText = { TimeText() }) {
            VacationScreen(today = LocalDate.of(2026, 11, 9), onConfirm = {})
        }
    }
}
