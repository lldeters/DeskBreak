package dev.stepcron.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dev.stepcron.wear.BuildConfig
import dev.stepcron.wear.Permissions
import dev.stepcron.wear.ui.home.HomeScreen
import dev.stepcron.wear.ui.stats.StatsScreen
import kotlinx.coroutines.flow.first

private object Routes {
    const val HOME = "home"
    const val STATS = "stats"
}

/**
 * Eine Instanz für die ganze App: Mit einer neuen Instanz pro Recomposition würde
 * rememberLauncherForActivityResult den Launcher jedes Mal ab- und wieder anmelden.
 */
private val RequestPermissionsContract = ActivityResultContracts.RequestMultiplePermissions()

/** Navigation (Wischen zum Zurückgehen) und Berechtigungs-Flow. */
@Composable
fun DeskBreakNavHost(viewModel: ReminderViewModel) {
    val context = LocalContext.current
    // Als State-Objekt (ohne `by`): Dieser Scope liest den Wert nicht selbst und wird daher nicht bei
    // jeder neuen Emission (Countdown-Ticks, Speichervorgänge) neu ausgeführt – nur der Home-Screen.
    val homeState = viewModel.homeUiState.collectAsStateWithLifecycle()
    val navController = rememberSwipeDismissableNavController()
    // Oberhalb des NavHosts, damit die Home-Liste beim Zurückwischen von der Statistik schon
    // initialisiert ist: Ein neuer Listenzustand blendet seine Items erst nach 2–3 Frames ein.
    val homeListState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    // Nach dem Dialog folgt immer ON_RESUME: MainActivity.onResume liest die Berechtigungen neu.
    val permissionLauncher = rememberLauncherForActivityResult(RequestPermissionsContract) { }

    fun requestMissingPermissions() {
        val missing = Permissions.missingRuntimePermissions(context)
        if (missing.isNotEmpty()) permissionLauncher.launch(missing)
    }

    // Erster Start: Notifications & Activity Recognition einmalig automatisch anfragen.
    LaunchedEffect(Unit) {
        snapshotFlow { homeState.value }.first { !it.loading && !it.permissionsRequested }
        viewModel.markPermissionsRequested()
        requestMissingPermissions()
    }

    // Eine gemeinsame Uhrzeit für alle Bildschirme: Sie wird nicht bei jedem Bildschirmwechsel neu
    // aufgebaut (Formatsuche, Broadcast-Registrierung) und bleibt bei Übergängen ruhig stehen.
    Scaffold(modifier = Modifier.fillMaxSize(), timeText = { TimeText() }) {
        SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    state = homeState.value,
                    listState = homeListState,
                    onEnabledChange = { enabled ->
                        viewModel.setEnabled(enabled)
                        if (enabled) requestMissingPermissions()
                    },
                    onManualReset = viewModel::manualReset,
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onIntervalStep = viewModel::changeInterval,
                    onStartHourStep = viewModel::changeStartHour,
                    onEndHourStep = viewModel::changeEndHour,
                    onAutoStepChange = { enabled ->
                        viewModel.setAutoStepEnabled(enabled)
                        if (enabled) requestMissingPermissions()
                    },
                    onRequestPermissions = {
                        val missing = Permissions.missingRuntimePermissions(context)
                        val activity = context.findActivity()
                        // Nach zweimaliger Ablehnung zeigt Android keinen Dialog mehr → App-Einstellungen öffnen.
                        val permanentlyDenied = homeState.value.permissionsRequested && activity != null &&
                            missing.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
                        if (missing.isEmpty() || permanentlyDenied) {
                            SystemSettings.openAppSettings(context)
                        } else {
                            permissionLauncher.launch(missing)
                        }
                    },
                    onOpenExactAlarmSettings = { SystemSettings.openExactAlarmSettings(context) },
                )
            }
            composable(Routes.STATS) {
                val statsState by viewModel.statsUiState.collectAsStateWithLifecycle()
                StatsScreen(
                    state = statsState,
                    showDebugTools = BuildConfig.DEBUG,
                    onSeedDemoData = viewModel::seedDemoData,
                    onClearHistory = viewModel::clearHistory,
                )
            }
        }
    }
}
