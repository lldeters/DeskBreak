package dev.stepcron.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dev.stepcron.wear.BuildConfig
import dev.stepcron.wear.Permissions
import dev.stepcron.wear.ui.home.HomeScreen
import dev.stepcron.wear.ui.stats.StatsScreen

private object Routes {
    const val HOME = "home"
    const val STATS = "stats"
}

/** Navigation (Wischen zum Zurückgehen) und Berechtigungs-Flow. */
@Composable
fun DeskBreakNavHost(viewModel: ReminderViewModel) {
    val context = LocalContext.current
    val homeState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val navController = rememberSwipeDismissableNavController()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.onResume() }

    fun requestMissingPermissions() {
        val missing = Permissions.missingRuntimePermissions(context)
        if (missing.isNotEmpty()) permissionLauncher.launch(missing)
    }

    // Erster Start: Notifications & Activity Recognition einmalig automatisch anfragen.
    LaunchedEffect(homeState.loading, homeState.permissionsRequested) {
        if (!homeState.loading && !homeState.permissionsRequested) {
            viewModel.markPermissionsRequested()
            requestMissingPermissions()
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                state = homeState,
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
                    val permanentlyDenied = homeState.permissionsRequested && activity != null &&
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
