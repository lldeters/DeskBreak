package dev.stepcron.mobile.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.stepcron.mobile.R
import dev.stepcron.mobile.ui.history.HistoryScreen
import dev.stepcron.mobile.ui.settings.SettingsScreen
import dev.stepcron.mobile.ui.today.TodayScreen

/** Die drei Seiten der Navigationsleiste. */
private enum class Destination(
    val route: String,
    @param:StringRes val label: Int,
    @param:StringRes val title: Int,
    val icon: ImageVector,
) {
    TODAY("today", R.string.nav_today, R.string.title_today, Icons.Filled.Home),
    HISTORY("history", R.string.nav_history, R.string.title_history, Icons.Filled.DateRange),
    SETTINGS("settings", R.string.nav_settings, R.string.title_settings, Icons.Filled.Settings),
}

/** Gerüst der App: Titelleiste, Navigationsleiste unten, Snackbar für Meldungen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeskBreakPhoneRoot(viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = Destination.entries.firstOrNull { it.route == backStackEntry?.destination?.route } ?: Destination.TODAY
    val snackbarHostState = remember { SnackbarHostState() }

    val message = state.local.message
    val messageText = message?.let { userMessageText(it.message) }
    LaunchedEffect(message?.id) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.messageShown(message.id)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(current.title)) }) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.TODAY.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable(Destination.TODAY.route) { TodayScreen(state = state, onSyncNow = viewModel::syncNow) }
            composable(Destination.HISTORY.route) { HistoryScreen(state = state) }
            composable(Destination.SETTINGS.route) { SettingsScreen(state = state, viewModel = viewModel) }
        }
    }
}

@Composable
private fun userMessageText(message: UserMessage): String = stringResource(
    when (message) {
        UserMessage.CSV_EXPORTED -> R.string.message_csv_exported
        UserMessage.BACKUP_EXPORTED -> R.string.message_backup_exported
        UserMessage.EXPORT_FAILED -> R.string.message_export_failed
        UserMessage.NOTHING_TO_EXPORT -> R.string.message_nothing_to_export
        UserMessage.BACKUP_UNREADABLE -> R.string.message_backup_unreadable
        UserMessage.BACKUP_TOO_NEW -> R.string.message_backup_too_new
        UserMessage.BACKUP_TOO_LARGE -> R.string.message_backup_too_large
        UserMessage.READ_FAILED -> R.string.message_read_failed
        UserMessage.NO_FILE_APP -> R.string.message_no_file_app
    },
)
