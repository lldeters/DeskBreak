package dev.stepcron.wear.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dev.stepcron.wear.BuildConfig
import dev.stepcron.wear.Permissions
import dev.stepcron.wear.R
import dev.stepcron.wear.domain.PauseOptions
import dev.stepcron.wear.domain.WeeklySchedule
import dev.stepcron.wear.service.ReminderNotifier
import dev.stepcron.wear.ui.exercise.ExerciseScreen
import dev.stepcron.wear.ui.home.HomeScreen
import dev.stepcron.wear.ui.license.LicenseScreen
import dev.stepcron.wear.ui.license.LicenseViewModel
import dev.stepcron.wear.ui.pause.PauseScreen
import dev.stepcron.wear.ui.pause.VacationScreen
import dev.stepcron.wear.ui.settings.ReminderSettingsScreen
import dev.stepcron.wear.ui.settings.ReminderSettingsUi
import dev.stepcron.wear.ui.schedule.DayScheduleScreen
import dev.stepcron.wear.ui.schedule.ScheduleScreen
import dev.stepcron.wear.ui.schedule.TimeOptions
import dev.stepcron.wear.ui.schedule.TimePickerScreen
import dev.stepcron.wear.ui.stats.StatsScreen
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

private object Routes {
    const val HOME = "home"
    const val STATS = "stats"
    const val SCHEDULE = "schedule"
    const val ARG_DAY = "day"
    const val DAY = "schedule/{$ARG_DAY}"
    const val DAY_START = "schedule/{$ARG_DAY}/start"
    const val DAY_END = "schedule/{$ARG_DAY}/end"
    const val REMINDER_SETTINGS = "reminder_settings"
    const val PAUSE = "pause"
    const val VACATION = "pause/vacation"
    const val ARG_EXERCISE = "index"
    const val EXERCISE = "exercise/{$ARG_EXERCISE}"
    const val LICENSE = "license"

    fun day(day: DayOfWeek) = "schedule/${day.value}"
    fun dayStart(day: DayOfWeek) = "schedule/${day.value}/start"
    fun dayEnd(day: DayOfWeek) = "schedule/${day.value}/end"
    fun exercise(index: Int) = "exercise/$index"

    val dayArguments = listOf(navArgument(ARG_DAY) { type = NavType.IntType })

    /** Wochentag aus der Route (ISO 1–7) oder `null` bei einem ungültigen Wert. */
    fun dayOf(entry: NavBackStackEntry): DayOfWeek? =
        entry.arguments?.getInt(ARG_DAY)?.takeIf { it in 1..7 }?.let(DayOfWeek::of)
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
    // Der Wert wird nur in den Zielen (Home direkt, die übrigen über derivedStateOf), im Effekt und in Callbacks
    // gelesen, nie im Rumpf dieser Funktion: So läuft sie nicht bei jeder neuen Emission (Countdown-Ticks,
    // Speichervorgänge) erneut. Das State-Objekt (statt `by`) macht ein versehentliches Lesen hier sichtbar.
    val homeState = viewModel.homeUiState.collectAsStateWithLifecycle()
    val navController = rememberSwipeDismissableNavController()
    // Oberhalb des NavHosts, damit die Home-Liste beim Zurückwischen von der Statistik schon
    // initialisiert ist: Ein neuer Listenzustand blendet seine Items erst nach 2–3 Frames ein.
    val homeListState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    // Ebenso für den Zeitplan, zu dem man von jedem Wochentag zurückwischt, und für den Wochentag, zu dem die
    // Uhrzeit-Auswahl zurückkehrt. Jeder neue Besuch (Navigation nach vorn) beginnt aber wieder oben.
    var scheduleVisit by rememberSaveable { mutableIntStateOf(0) }
    val scheduleListState = rememberSaveable(scheduleVisit, saver = ScalingLazyListState.Saver) {
        ScalingLazyListState(initialCenterItemIndex = 0)
    }
    var dayVisit by rememberSaveable { mutableIntStateOf(0) }
    val dayListState = rememberSaveable(dayVisit, saver = ScalingLazyListState.Saver) {
        ScalingLazyListState(initialCenterItemIndex = 0)
    }
    val openSchedule = {
        scheduleVisit++
        navController.navigate(Routes.SCHEDULE)
    }

    // Nach dem Dialog folgt immer ON_RESUME: MainActivity.onResume liest die Berechtigungen neu.
    val permissionLauncher = rememberLauncherForActivityResult(RequestPermissionsContract) { }

    fun requestMissingPermissions() {
        val missing = Permissions.missingRuntimePermissions(context)
        if (missing.isNotEmpty()) permissionLauncher.launch(missing)
    }

    // Aktion „1-Min-Übung" der Erinnerung: Beim Kaltstart steht sie im Start-Intent, läuft die App schon,
    // kommt sie per onNewIntent (singleTop). Bewusst kein Deep Link der Navigation: Der baut bei Intents mit
    // FLAG_ACTIVITY_NEW_TASK – so startet das System Notification-Aktionen – die Activity neu auf.
    val activity = context.findActivity() as? ComponentActivity
    var launchIntentHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(activity, navController) {
        if (activity == null) return@LaunchedEffect
        fun openExercise(intent: Intent?) {
            val index = ReminderNotifier.exerciseIndexOf(intent) ?: return
            navController.navigate(Routes.exercise(index)) { popUpTo(Routes.HOME) }
        }
        navController.currentBackStackEntryFlow.first() // Graph steht
        // Nur einmal: Nach Konfigurationswechsel oder Prozessende liefert die Activity denselben Start-Intent.
        if (!launchIntentHandled) {
            launchIntentHandled = true
            openExercise(activity.intent)
        }
        callbackFlow {
            val listener = Consumer<Intent> { trySend(it) }
            activity.addOnNewIntentListener(listener)
            awaitClose { activity.removeOnNewIntentListener(listener) }
        }.collect { openExercise(it) }
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
                    onOpenPause = { navController.navigate(Routes.PAUSE) },
                    onResume = viewModel::resume,
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onIntervalStep = viewModel::changeInterval,
                    onGoalStep = viewModel::changeDailyGoal,
                    onOpenSchedule = openSchedule,
                    onOpenReminderSettings = { navController.navigate(Routes.REMINDER_SETTINGS) },
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
                    onOpenLicense = { navController.navigate(Routes.LICENSE) },
                )
            }
            composable(Routes.LICENSE) {
                // Eigenes ViewModel für die Dauer des Besuchs: Es fragt beim Öffnen bei Play nach.
                val licenseViewModel: LicenseViewModel = viewModel(factory = LicenseViewModel.Factory)
                val licenseState by licenseViewModel.uiState.collectAsStateWithLifecycle()
                LicenseScreen(
                    state = licenseState,
                    onUnlockOnPhone = licenseViewModel::unlockOnPhone,
                    onBuy = { unlock -> context.findActivity()?.let { licenseViewModel.buy(it, unlock) } },
                    onCheckPurchases = licenseViewModel::checkPurchases,
                    onManageSubscription = licenseViewModel::manageSubscription,
                )
            }
            composable(Routes.STATS) {
                val statsState by viewModel.statsUiState.collectAsStateWithLifecycle()
                // Aus dem schon geladenen Home-Zustand: Die Karte „Heute" hat so ab dem ersten Frame das
                // richtige Layout, auch während die Statistik noch lädt.
                val today by remember { derivedStateOf { homeState.value.today } }
                StatsScreen(
                    state = statsState,
                    today = today,
                    showDebugTools = BuildConfig.DEBUG,
                    onSeedDemoData = viewModel::seedDemoData,
                    onClearHistory = viewModel::clearHistory,
                )
            }
            composable(
                route = Routes.EXERCISE,
                arguments = listOf(navArgument(Routes.ARG_EXERCISE) { type = NavType.IntType }),
            ) { entry ->
                val exercises = stringArrayResource(R.array.exercises)
                val index = entry.arguments?.getInt(Routes.ARG_EXERCISE) ?: 0
                ExerciseScreen(
                    exercise = exercises[Math.floorMod(index, exercises.size)],
                    onCompleted = viewModel::completeExercise,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.REMINDER_SETTINGS) {
                val settings by remember(homeState) {
                    derivedStateOf {
                        homeState.value.takeUnless { it.loading }?.let {
                            ReminderSettingsUi(
                                snoozeMinutes = it.snoozeMinutes,
                                vibration = it.vibration,
                                stepThreshold = it.stepThreshold,
                                stepDetectionAvailable = it.autoStepEnabled && it.system.stepSensorAvailable,
                                respectQuietModes = it.respectQuietModes,
                                onlyWhenWorn = it.onlyWhenWorn,
                            )
                        }
                    }
                }
                ReminderSettingsScreen(
                    settings = settings,
                    onSnoozeStep = viewModel::changeSnooze,
                    onNextVibration = viewModel::nextVibration,
                    onStepThresholdStep = viewModel::changeStepThreshold,
                    onRespectQuietModesChange = viewModel::setRespectQuietModes,
                    onOnlyWhenWornChange = viewModel::setOnlyWhenWorn,
                )
            }
            composable(Routes.PAUSE) {
                val schedule by rememberLoadedSchedule(homeState)
                PauseScreen(
                    schedule = schedule,
                    onPauseUntil = { until ->
                        viewModel.pauseUntil(until)
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    },
                    onOpenVacation = { navController.navigate(Routes.VACATION) },
                )
            }
            composable(Routes.VACATION) {
                val today = remember { LocalDate.now() }
                VacationScreen(
                    today = today,
                    onConfirm = { lastDay ->
                        viewModel.pauseUntil(PauseOptions.vacationUntil(lastDay, ZoneId.systemDefault()))
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    },
                )
            }
            composable(Routes.SCHEDULE) {
                val schedule by rememberLoadedSchedule(homeState)
                ScheduleScreen(
                    schedule = schedule,
                    listState = scheduleListState,
                    onDayEnabledChange = viewModel::setDayEnabled,
                    onOpenDay = { day ->
                        dayVisit++
                        navController.navigate(Routes.day(day))
                    },
                )
            }
            composable(Routes.DAY, arguments = Routes.dayArguments) { entry ->
                val day = Routes.dayOf(entry) ?: return@composable
                val schedule by rememberLoadedSchedule(homeState)
                DayScheduleScreen(
                    listState = dayListState,
                    day = day,
                    schedule = schedule,
                    onEnabledChange = { enabled -> viewModel.setDayEnabled(day, enabled) },
                    onEditStart = { navController.navigate(Routes.dayStart(day)) },
                    onEditEnd = { navController.navigate(Routes.dayEnd(day)) },
                    onCopyToAll = { viewModel.copyDayToAll(day) },
                )
            }
            composable(Routes.DAY_START, arguments = Routes.dayArguments) { entry ->
                val day = Routes.dayOf(entry) ?: return@composable
                val window = rememberLoadedSchedule(homeState).value?.get(day)?.window ?: return@composable
                TimePickerScreen(
                    title = stringResource(R.string.schedule_start_label),
                    options = remember(window) { TimeOptions.startOptions(window) },
                    initialMinute = window.startMinute,
                    onConfirm = { minute ->
                        viewModel.setDayStart(day, minute)
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.DAY_END, arguments = Routes.dayArguments) { entry ->
                val day = Routes.dayOf(entry) ?: return@composable
                val window = rememberLoadedSchedule(homeState).value?.get(day)?.window ?: return@composable
                TimePickerScreen(
                    title = stringResource(R.string.schedule_end_label),
                    options = remember(window) { TimeOptions.endOptions(window) },
                    initialMinute = window.endMinute,
                    onConfirm = { minute ->
                        viewModel.setDayEnd(day, minute)
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}

/**
 * Wochenplan aus dem Home-Zustand, `null` solange die Einstellungen laden. Über derivedStateOf: Countdown-Ticks
 * und andere Zustandsänderungen lösen hier keine Recomposition aus, nur ein geänderter Plan.
 */
@Composable
private fun rememberLoadedSchedule(homeState: State<HomeUiState>): State<WeeklySchedule?> =
    remember(homeState) { derivedStateOf { homeState.value.takeUnless { it.loading }?.schedule } }
