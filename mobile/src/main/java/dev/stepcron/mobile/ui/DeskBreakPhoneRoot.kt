package dev.stepcron.mobile.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import dev.stepcron.mobile.ui.today.TodayActions
import dev.stepcron.mobile.ui.today.TodayScreen

/** Die drei Seiten der Navigationsleiste. */
private enum class Destination(
    val route: String,
    @param:StringRes val label: Int,
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
) {
    TODAY("today", R.string.nav_today, R.string.title_today, R.drawable.ic_today),
    HISTORY("history", R.string.nav_history, R.string.title_history, R.drawable.ic_bar_chart),
    SETTINGS("settings", R.string.nav_settings, R.string.title_settings, R.drawable.ic_tune),
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

    fun open(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(current.title)) },
                actions = { SyncAction(syncing = state.sync.syncing, onSync = viewModel::syncNow) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = { open(destination) },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
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
            composable(Destination.TODAY.route) {
                TodayScreen(
                    state = state,
                    actions = TodayActions(
                        onSyncNow = viewModel::syncNow,
                        onPauseOneHour = viewModel::pauseOneHour,
                        onResume = viewModel::endPause,
                        onEnable = { viewModel.setEnabled(true) },
                        onOpenSettings = { open(Destination.SETTINGS) },
                    ),
                )
            }
            composable(Destination.HISTORY.route) { HistoryScreen(state = state) }
            composable(Destination.SETTINGS.route) { SettingsScreen(state = state, viewModel = viewModel) }
        }
    }
}

/** Abgleich in der Titelleiste – auf jeder Seite erreichbar; dreht sich, solange er läuft. */
@Composable
private fun SyncAction(syncing: Boolean, onSync: () -> Unit) {
    val label = stringResource(if (syncing) R.string.empty_syncing else R.string.sync_now)
    IconButton(onClick = onSync, enabled = !syncing, modifier = Modifier.semantics { contentDescription = label }) {
        if (syncing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(painterResource(R.drawable.ic_sync), contentDescription = null)
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
