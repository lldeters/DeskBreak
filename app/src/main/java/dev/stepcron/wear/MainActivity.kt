package dev.stepcron.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dev.stepcron.wear.ui.DeskBreakNavHost
import dev.stepcron.wear.ui.ReminderViewModel
import dev.stepcron.wear.ui.theme.DeskBreakTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ReminderViewModel by viewModels { ReminderViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DeskBreakTheme {
                DeskBreakNavHost(viewModel)
            }
        }
    }
}
