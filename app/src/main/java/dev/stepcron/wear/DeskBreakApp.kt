package dev.stepcron.wear

import android.app.Application
import android.content.Context
import dev.stepcron.wear.data.DemoDataSeeder
import dev.stepcron.wear.data.MovementRepository
import dev.stepcron.wear.data.db.DeskBreakDatabase
import dev.stepcron.wear.data.settings.SettingsRepository
import dev.stepcron.wear.service.AlarmScheduler
import dev.stepcron.wear.service.AndroidDeviceState
import dev.stepcron.wear.service.ReminderEngine
import dev.stepcron.wear.service.ReminderNotifier
import dev.stepcron.wear.service.ServiceStepTracking
import dev.stepcron.wear.surface.SurfaceUpdates

class DeskBreakApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.notifier.createChannels()
    }
}

/** Schlanker Service-Locator für die prozessweiten Singletons (kein DI-Framework nötig). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: DeskBreakDatabase by lazy { DeskBreakDatabase.create(appContext) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val movementRepository: MovementRepository by lazy { MovementRepository { database.movementDao() } }
    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(appContext) }
    val notifier: ReminderNotifier by lazy { ReminderNotifier(appContext) }
    val engine: ReminderEngine by lazy {
        ReminderEngine(
            settingsRepository = settingsRepository,
            movementLog = movementRepository,
            alarms = alarmScheduler,
            alerts = notifier,
            stepTracking = ServiceStepTracking(appContext),
            deviceState = AndroidDeviceState(appContext),
            onStateChanged = { SurfaceUpdates.request(appContext) },
        )
    }
    val demoDataSeeder: DemoDataSeeder by lazy { DemoDataSeeder(movementRepository) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DeskBreakApp).container
