package dev.stepcron.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.stepcron.mobile.ui.DeskBreakPhoneRoot
import dev.stepcron.mobile.ui.theme.DeskBreakPhoneTheme

class MainActivity : ComponentActivity() {

    private val repository get() = (application as DeskBreakPhoneApp).repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeskBreakPhoneTheme {
                DeskBreakPhoneRoot()
            }
        }
    }

    /** Im Vordergrund: Listener des Data Layers an und um einen frischen Stand bitten. */
    override fun onStart() {
        super.onStart()
        repository.onForeground()
    }

    override fun onStop() {
        repository.onBackground()
        super.onStop()
    }
}
