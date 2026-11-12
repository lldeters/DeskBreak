package dev.stepcron.wear

import android.app.Application
import android.content.Context
import android.util.Log
import dev.stepcron.billing.BillingGateway
import dev.stepcron.billing.PlayBillingGateway
import dev.stepcron.shared.license.Unlock
import dev.stepcron.wear.data.DemoDataSeeder
import dev.stepcron.wear.data.MovementRepository
import dev.stepcron.wear.data.db.DeskBreakDatabase
import dev.stepcron.wear.data.license.LicenseRepository
import dev.stepcron.wear.data.license.WatchLicense
import dev.stepcron.wear.data.settings.SettingsRepository
import dev.stepcron.wear.service.AlarmScheduler
import dev.stepcron.wear.service.AndroidDeviceState
import dev.stepcron.wear.service.LicenseCheck
import dev.stepcron.wear.service.ReminderEngine
import dev.stepcron.wear.service.ReminderNotifier
import dev.stepcron.wear.service.ServiceStepTracking
import dev.stepcron.wear.surface.SurfaceUpdates
import dev.stepcron.wear.sync.BackupRestorer
import dev.stepcron.wear.sync.DataLayer
import dev.stepcron.wear.sync.SettingsUpdateApplier
import dev.stepcron.wear.sync.SnapshotBuilder
import dev.stepcron.wear.sync.WatchSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class DeskBreakApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.notifier.createChannels()
        container.startLicense()
    }
}

/** Schlanker Service-Locator für die prozessweiten Singletons (kein DI-Framework nötig). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Prozessweiter Scope für Arbeit, die keinem Bildschirm gehört (z. B. der verzögerte Abgleich mit dem Handy). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
            licenseCheck = LicenseCheck { now -> watchLicense.isActive(now) },
            onStateChanged = {
                SurfaceUpdates.request(appContext)
                watchSync.requestPublish()
            },
        )
    }
    val demoDataSeeder: DemoDataSeeder by lazy { DemoDataSeeder(movementRepository) }

    // Abgleich mit der Handy-App (optional – ohne Handy bleibt er wirkungslos).
    val dataLayer: DataLayer by lazy { DataLayer(appContext) }
    val watchSync: WatchSync by lazy {
        WatchSync(SnapshotBuilder(settingsRepository, movementRepository, licenseRepository), dataLayer, appScope)
    }
    val settingsUpdateApplier: SettingsUpdateApplier by lazy { SettingsUpdateApplier(settingsRepository, engine) }
    val backupRestorer: BackupRestorer by lazy {
        BackupRestorer(
            history = movementRepository,
            settingsApplier = settingsUpdateApplier,
            describe = { failure -> appContext.getString(failure.messageRes) },
        )
    }

    // Freischaltung: Testphase, Käufe über Google Play und die Meldungen des Handys.
    val licenseRepository: LicenseRepository by lazy { LicenseRepository(appContext) }
    val watchLicense: WatchLicense by lazy {
        WatchLicense(
            repository = licenseRepository,
            onActiveChanged = { engine.resync() },
            onRecordChanged = { watchSync.requestPublish() },
            requestPlayCheck = ::refreshBilling,
        )
    }

    /** Google Play Billing der Uhr; verbindet sich erst beim ersten [BillingGateway.refresh]. */
    val billing: BillingGateway by lazy { PlayBillingGateway(appContext, appScope) }

    /** Fragt Angebote und Käufe bei Play neu ab (beim Öffnen der App bzw. im Hintergrund); kehrt sofort zurück. */
    fun refreshBilling() {
        appScope.launch { billing.refresh() }
    }

    /**
     * Beim Prozessstart: die Testphase beginnen (auch bei Installationen von vor der Freischaltung) und jede erfolgreiche
     * Play-Abfrage der Uhr in die Lizenz übernehmen – die meldet sie dem Handy und gleicht die Erinnerungen ab.
     */
    fun startLicense() {
        appScope.launch { logFailure("Testphase beginnen") { watchLicense.startTrial() } }
        appScope.launch {
            billing.state
                .mapNotNull { state -> state.owned?.let { owned -> CheckedPurchases(owned, state.checkedAtMillis) } }
                // Jede neue erfolgreiche Abfrage genau einmal (gleicher Stand zur gleichen Zeit ist dieselbe).
                .distinctUntilChanged()
                .collect { check ->
                    logFailure("Play-Abfrage übernehmen") { watchLicense.applyOwnCheck(check.owned, check.checkedAtMillis) }
                }
        }
    }

    /** Eine erfolgreiche Play-Abfrage dieser Uhr. */
    private data class CheckedPurchases(val owned: Set<Unlock>, val checkedAtMillis: Long)

    /** Arbeit im [appScope]: Ein Fehler (z. B. DataStore) wird protokolliert und darf die App nicht beenden. */
    private suspend fun logFailure(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$what fehlgeschlagen", e)
        }
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as DeskBreakApp).container
