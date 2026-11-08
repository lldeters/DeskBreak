package dev.stepcron.wear

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dev.stepcron.wear.ui.DeskBreakNavHost
import dev.stepcron.wear.ui.ReminderViewModel
import dev.stepcron.wear.ui.theme.DeskBreakTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    private val viewModel: ReminderViewModel by viewModels { ReminderViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Das Startfenster stehen lassen, bis der gespeicherte Zustand geladen ist (höchstens
        // INITIAL_STATE_TIMEOUT_MILLIS): So wird Home einmal mit echten Daten aufgebaut, statt erst mit
        // Platzhaltern und kurz darauf – mitten in der Öffnungsanimation – komplett neu.
        var contentReady = false
        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!contentReady) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
        lifecycleScope.launch {
            // Nach einer Konfigurationsänderung ist der Zustand schon geladen: kein Warten.
            withTimeoutOrNull(INITIAL_STATE_TIMEOUT_MILLIS) {
                viewModel.homeUiState.first { !it.loading }
            }
            setContent {
                DeskBreakTheme {
                    DeskBreakNavHost(viewModel)
                }
            }
            contentReady = true
        }
    }

    override fun onResume() {
        super.onResume()
        // Unabhängig von der Komposition: Berechtigungen neu lesen und Timer abgleichen
        // (auch nach dem Berechtigungsdialog, der die Activity pausiert).
        viewModel.onResume()
    }

    private companion object {
        const val INITIAL_STATE_TIMEOUT_MILLIS = 250L
    }
}
