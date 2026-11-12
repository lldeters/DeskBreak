package dev.stepcron.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.stepcron.mobile.ui.DeskBreakPhoneRoot
import dev.stepcron.mobile.ui.theme.DeskBreakPhoneTheme

class MainActivity : ComponentActivity() {

    private val app get() = application as DeskBreakPhoneApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeskBreakPhoneTheme {
                DeskBreakPhoneRoot()
            }
        }
    }

    /** Im Vordergrund: Listener des Data Layers an, um einen frischen Stand bitten und Käufe bei Play abfragen. */
    override fun onStart() {
        super.onStart()
        app.repository.onForeground()
        app.license.onForeground()
    }

    override fun onStop() {
        app.repository.onBackground()
        super.onStop()
    }
}
