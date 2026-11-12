package dev.stepcron.mobile.sync

import dev.stepcron.mobile.settings.SettingsRules
import dev.stepcron.shared.license.LicenseCodec
import dev.stepcron.shared.license.LicenseInfo
import dev.stepcron.shared.sync.RestoreResult
import dev.stepcron.shared.sync.SettingsUpdate
import dev.stepcron.shared.sync.SyncBackup
import dev.stepcron.shared.sync.SyncCodec
import dev.stepcron.shared.sync.SyncPaths
import dev.stepcron.shared.sync.SyncSettings
import dev.stepcron.shared.sync.SyncSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Zeitgrenzen des Abgleichs; in Tests kürzer. */
data class SyncTiming(
    /** So lange wartet „Jetzt abgleichen" auf einen neuen Stand der Uhr. */
    val syncWaitMillis: Long = 15_000L,
    /** Änderungen, die so dicht aufeinander folgen (Stepper), gehen als eine Nachricht raus. */
    val sendDebounceMillis: Long = 300L,
    /** So lange wartet eine gesendete Änderung auf einen neueren Stand der Uhr als Bestätigung. */
    val confirmTimeoutMillis: Long = 20_000L,
    val restoreTimeoutMillis: Long = 60_000L,
    /**
     * Ein Stand, der so viel später als das Senden entstand, bestätigt eine Änderung auch mit abweichenden Werten
     * (Toleranz für die Uhrzeiten von Handy und Uhr).
     */
    val clockSkewMillis: Long = 2_000L,
    /**
     * Beim Öffnen der App wird immer um einen frischen Stand gebeten (die Uhr veröffentlicht im Hintergrund nicht
     * zuverlässig) – außer der letzte Versuch begann gerade erst (z. B. beim Drehen des Bildschirms).
     */
    val foregroundRefreshMillis: Long = 10_000L,
)

/** Stand der Uhr auf dem Handy. */
data class SnapshotState(
    /** Ist der gespeicherte Stand schon gelesen? Vorher ist offen, ob es einen gibt. */
    val loaded: Boolean = false,
    val snapshot: SyncSnapshot? = null,
    /** Wann der Stand auf dem Handy ankam. */
    val receivedAtMillis: Long? = null,
)

/** Zustand des Abgleichs. */
data class SyncState(
    val syncing: Boolean = false,
    /** Ergebnis des letzten Versuchs; `null` = alles in Ordnung. */
    val problem: SyncProblem? = null,
    val lastAttemptAtMillis: Long? = null,
)

/** Wo eine Einstellungs-Änderung gerade steht. */
sealed interface SettingsSendState {
    data object Idle : SettingsSendState
    data object Sending : SettingsSendState

    /** Bei der Uhr angekommen; wartet auf deren neuen Stand. */
    data object AwaitingConfirmation : SettingsSendState

    data class Confirmed(val atMillis: Long) : SettingsSendState

    /** Gesendet, aber kein neuer Stand der Uhr innerhalb der Wartezeit – die Anzeige zeigt wieder deren Stand. */
    data object Unconfirmed : SettingsSendState

    /** Nicht zugestellt; [update] lässt sich erneut senden. */
    data class Failed(val problem: SyncProblem, val update: SettingsUpdate) : SettingsSendState
}

/** Wo eine Wiederherstellung gerade steht. */
sealed interface RestoreState {
    data object Idle : RestoreState
    data object Sending : RestoreState
    data object WaitingForWatch : RestoreState
    data class Done(val restoredEvents: Int) : RestoreState
    data class Failed(val problem: SyncProblem) : RestoreState
}

/**
 * Einzige Quelle für den Stand der Uhr auf dem Handy: gespeicherter Snapshot ([SnapshotCache]), Abgleich über den
 * Data Layer ([WatchGateway]), Einstellungs-Änderungen mit sofortiger Anzeige und Bestätigung durch die Uhr sowie
 * die Wiederherstellung einer Sicherung. Lebt so lange wie der App-Prozess; Vordergrund-Listener und der
 * Hintergrunddienst liefern ihm neue Snapshots. Die Lizenz-Sicht der Uhr aus jedem gelesenen Snapshot geht an
 * [license]; dessen Sicht schickt jeder Abgleich der Uhr mit.
 */
class SyncRepository(
    private val gateway: WatchGateway,
    private val cache: SnapshotCache,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timing: SyncTiming = SyncTiming(),
    private val license: LicenseLink? = null,
) : GatewayListener {

    private val _snapshot = MutableStateFlow(SnapshotState())
    val snapshot: StateFlow<SnapshotState> = _snapshot.asStateFlow()

    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    /** Änderungen vom Handy, die der Stand der Uhr noch nicht bestätigt hat. */
    private val overlay = MutableStateFlow(SettingsUpdate())

    /** Noch nicht bestätigte Änderungen (für die Anzeige auf die Einstellungen des Snapshots anzuwenden). */
    val pendingChanges: StateFlow<SettingsUpdate> = overlay.asStateFlow()

    /** Einstellungen der Uhr samt noch nicht bestätigter Änderungen; `null` ohne Snapshot. */
    val settings: Flow<SyncSettings?> = combine(_snapshot, overlay) { state, pending ->
        state.snapshot?.settings?.let { SettingsRules.apply(it, pending) }
    }

    private val _settingsSend = MutableStateFlow<SettingsSendState>(SettingsSendState.Idle)
    val settingsSend: StateFlow<SettingsSendState> = _settingsSend.asStateFlow()

    private val _restore = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val restore: StateFlow<RestoreState> = _restore.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()
    private val ingestMutex = Mutex()

    /** Zählt ankommende Snapshots; „Jetzt abgleichen" wartet darauf, dass er wächst. */
    private val snapshotsSeen = MutableStateFlow(0L)

    /** Problem der zuletzt angekommenen Daten (neueres Format, unlesbar) oder `null`. */
    @Volatile
    private var lastDataProblem: SyncProblem? = null

    private val lock = Any()
    private var syncJob: Job? = null
    private var confirmJob: Job? = null

    /** Gesammelte, noch nicht gesendete Änderungen (unter [lock]). */
    private var unsent = SettingsUpdate()
    private var sending = false
    private var lastSentAtMillis = 0L
    private val sendTrigger = Channel<Unit>(Channel.CONFLATED)

    private val restoreWaiters = ConcurrentHashMap<String, CompletableDeferred<RestoreResult>>()

    init {
        scope.launch(ioDispatcher) { loadCache() }
        scope.launch {
            // Ein Verbraucher: sendet nacheinander, Änderungen während des Wartens gehen in dieselbe Nachricht.
            while (true) {
                sendTrigger.receive()
                delay(timing.sendDebounceMillis)
                flushSettings()
            }
        }
    }

    private fun loadCache() {
        _snapshot.value = try {
            val entry = cache.read()
            val snapshot = entry?.let { (SyncDecoding.snapshot(it.bytes) as? Decoded.Ok)?.value }
            snapshot?.license?.let { info -> scope.launch { mergeLicense(info) } }
            SnapshotState(
                loaded = true,
                snapshot = snapshot,
                receivedAtMillis = entry?.receivedAtMillis?.takeIf { snapshot != null },
            )
        } catch (e: Exception) {
            // Ein unlesbarer Speicher ist kein Grund abzustürzen: dann eben ohne gespeicherten Stand.
            SnapshotState(loaded = true)
        }
        loaded.complete(Unit)
    }

    suspend fun awaitLoaded() = loaded.await()

    // --- Abgleich ---

    /** Startet einen Abgleich, sofern nicht schon einer läuft, und liefert dessen Job. */
    fun refresh(): Job = synchronized(lock) {
        syncJob?.takeIf { it.isActive } ?: scope.launch { performSync() }.also { syncJob = it }
    }

    /** App kommt in den Vordergrund: Listener an und – wenn der letzte Versuch nicht gerade erst war – abgleichen. */
    fun onForeground() {
        gateway.startListening(this)
        val last = _sync.value.lastAttemptAtMillis
        if (last == null || clock() - last > timing.foregroundRefreshMillis) refresh()
    }

    fun onBackground() {
        gateway.stopListening()
    }

    private suspend fun performSync() {
        awaitLoaded()
        val startedAt = clock()
        _sync.update { it.copy(syncing = true) }
        var problem: SyncProblem? = null
        try {
            problem = guarded { syncOnce() }
        } finally {
            // Auch bei Abbruch: „Abgleich läuft" darf nicht stehen bleiben.
            _sync.value = SyncState(syncing = false, problem = problem, lastAttemptAtMillis = startedAt)
        }
    }

    private suspend fun syncOnce(): SyncProblem? {
        gateway.checkAvailability()?.let { return it }
        // Was der Data Layer schon vorhält – auch ohne Verbindung zur Uhr.
        readStored()
        val nodes = gateway.findWatches()
        val targets = nodes.targets
        if (targets.isEmpty()) return lastDataProblem ?: SyncProblem.NoWatch

        val seenBefore = snapshotsSeen.value
        sendLicense(targets.map { it.id })
        gateway.sendMessage(targets.map { it.id }, SyncPaths.REQUEST_SYNC, ByteArray(0))
        val answered = withTimeoutOrNull(timing.syncWaitMillis) { snapshotsSeen.first { it > seenBefore } } != null
        // Ohne Änderungsereignis (z. B. Listener noch nicht aktiv) noch einmal direkt nachsehen.
        if (!answered) readStored()
        val fresh = snapshotsSeen.value > seenBefore
        return when {
            fresh -> lastDataProblem
            nodes.withApp.isEmpty() -> SyncProblem.AppMissingOnWatch
            _snapshot.value.snapshot == null -> lastDataProblem ?: SyncProblem.NoDataYet
            else -> lastDataProblem ?: SyncProblem.WatchDidNotAnswer
        }
    }

    /** Liest die Snapshots im Data Layer und übernimmt den neuesten, falls er neuer ist als der gespeicherte. */
    private suspend fun readStored() {
        val items = gateway.readSnapshots().map { it to SyncDecoding.snapshot(it) }
        if (items.isEmpty()) return
        val newest = items
            .filter { it.second is Decoded.Ok }
            .maxByOrNull { (it.second as Decoded.Ok).value.generatedAtMillis }
            ?: items.first()
        ingest(newest.first, newest.second, fromEvent = false)
    }

    /**
     * Übernimmt einen Snapshot (Rohdaten des Assets) aus einem Änderungsereignis – vom Vordergrund-Listener oder
     * vom Hintergrunddienst. Liefert `true`, wenn er als neuer Stand gespeichert wurde.
     */
    suspend fun ingestSnapshot(bytes: ByteArray): Boolean =
        ingest(bytes, withContext(Dispatchers.Default) { SyncDecoding.snapshot(bytes) }, fromEvent = true)

    private suspend fun ingest(bytes: ByteArray, decoded: Decoded<SyncSnapshot>, fromEvent: Boolean): Boolean {
        awaitLoaded()
        val isNew = storeSnapshot(bytes, decoded, fromEvent)
        // Auch aus einem nicht neueren Stand: withPeerInfo übernimmt nur, was nicht älter ist als das Bekannte.
        if (decoded is Decoded.Ok) mergeLicense(decoded.value.license)
        return isNew
    }

    /** Speichert und zeigt [decoded], falls er neuer ist; liefert `true`, wenn er als neuer Stand gespeichert wurde. */
    private suspend fun storeSnapshot(bytes: ByteArray, decoded: Decoded<SyncSnapshot>, fromEvent: Boolean): Boolean =
        ingestMutex.withLock {
            when (decoded) {
                is Decoded.TooNew -> {
                    lastDataProblem = SyncProblem.DataTooNew(decoded.version)
                    if (fromEvent) snapshotsSeen.update { it + 1 }
                    false
                }

                Decoded.Invalid -> {
                    lastDataProblem = SyncProblem.InvalidData
                    if (fromEvent) snapshotsSeen.update { it + 1 }
                    false
                }

                is Decoded.Ok -> {
                    val snapshot = decoded.value
                    val current = _snapshot.value.snapshot
                    val isNew = when {
                        current == null -> true
                        // Ein Ereignis ist immer der neueste Stand seiner Uhr (auch nach einer Zeitkorrektur dort);
                        // nur Doppelte (Vordergrund-Listener und Dienst) werden übergangen.
                        fromEvent -> snapshot.generatedAtMillis != current.generatedAtMillis
                        else -> snapshot.generatedAtMillis > current.generatedAtMillis
                    }
                    lastDataProblem = null
                    if (isNew) {
                        val now = clock()
                        withContext(ioDispatcher) {
                            try {
                                cache.write(bytes, now)
                            } catch (e: IOException) {
                                // Anzeige trotzdem aktualisieren; beim nächsten Stand wird wieder gespeichert.
                            } catch (e: IllegalArgumentException) {
                                // zu groß für den Speicher – ebenso
                            }
                        }
                        _snapshot.value = SnapshotState(loaded = true, snapshot = snapshot, receivedAtMillis = now)
                        onSnapshotAccepted(snapshot, now)
                    }
                    if (isNew || fromEvent) snapshotsSeen.update { it + 1 }
                    isNew
                }
            }
        }

    // --- Lizenz ---

    /** Übernimmt die Lizenz-Sicht der Uhr; ein Fehler dabei ist kein Fehler des Abgleichs. */
    private suspend fun mergeLicense(info: LicenseInfo?) {
        val link = license ?: return
        if (info == null) return
        try {
            link.onWatchLicense(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kommt mit dem nächsten Stand wieder.
        }
    }

    /** Meldet der Uhr die Lizenz-Sicht des Handys – vor der Bitte um einen Stand, damit der schon dazu passt. */
    private suspend fun sendLicense(nodeIds: List<String>) {
        val info = license?.licenseForWatch() ?: return
        try {
            gateway.sendMessage(nodeIds, SyncPaths.LICENSE, LicenseCodec.encodeInfo(info))
        } catch (e: SyncException) {
            // Nicht schlimm: Die Uhr erinnert weiter nach ihrem eigenen Stand; beim nächsten Abgleich geht es wieder mit.
        }
    }

    // --- Einstellungen ---

    /** Einstellungen samt noch nicht bestätigter Änderungen; `null` ohne Snapshot. */
    fun currentSettings(): SyncSettings? =
        _snapshot.value.snapshot?.settings?.let { SettingsRules.apply(it, overlay.value) }

    /** Zeigt eine Änderung sofort an und schickt sie (kurz gesammelt) an die Uhr. */
    fun changeSettings(update: SettingsUpdate) {
        synchronized(lock) {
            val base = currentSettings() ?: return
            val change = SettingsRules.diff(base, SettingsRules.apply(base, update))
            if (change.isEmpty) {
                if (_settingsSend.value is SettingsSendState.Failed) _settingsSend.value = SettingsSendState.Idle
                return
            }
            overlay.value = SettingsRules.merge(overlay.value, change)
            unsent = SettingsRules.merge(unsent, change)
            confirmJob?.cancel()
            _settingsSend.value = SettingsSendState.Sending
        }
        sendTrigger.trySend(Unit)
    }

    /** Sendet eine fehlgeschlagene Änderung noch einmal. */
    fun retrySettings() {
        val failed = _settingsSend.value as? SettingsSendState.Failed ?: return
        changeSettings(failed.update)
    }

    private suspend fun flushSettings() {
        val update = synchronized(lock) {
            val pending = unsent
            unsent = SettingsUpdate()
            sending = !pending.isEmpty
            pending
        }
        if (update.isEmpty) return
        var sentAt = 0L
        val problem = guarded {
            sentAt = sendSettings(update)
            null
        }
        synchronized(lock) {
            sending = false
            if (problem == null) {
                lastSentAtMillis = sentAt
                if (unsent.isEmpty) {
                    // Die Antwort der Uhr kann schon während des Sendens angekommen sein.
                    val current = _snapshot.value
                    val answered = current.snapshot?.let { confirms(it, current.receivedAtMillis ?: 0L) } == true
                    if (answered) {
                        confirmLocked()
                    } else {
                        _settingsSend.value = SettingsSendState.AwaitingConfirmation
                        startConfirmTimeout()
                    }
                }
            } else {
                // Nicht zugestellt: Anzeige wieder auf den Stand der Uhr (nur noch Ungesendetes bleibt sichtbar).
                overlay.value = unsent
                _settingsSend.value = SettingsSendState.Failed(problem, update)
            }
        }
    }

    /** Schickt [update] an die Uhr und bittet gleich um einen neuen Stand; liefert den Sendezeitpunkt. */
    private suspend fun sendSettings(update: SettingsUpdate): Long {
        gateway.checkAvailability()?.let { throw SyncException(it) }
        val targets = gateway.findWatches().targets.map { it.id }
        if (targets.isEmpty()) throw SyncException(SyncProblem.NoWatch)
        val sentAt = clock()
        gateway.sendMessage(targets, SyncPaths.UPDATE_SETTINGS, SyncCodec.encodeSettingsUpdate(update))
        try {
            gateway.sendMessage(targets, SyncPaths.REQUEST_SYNC, ByteArray(0))
        } catch (e: SyncException) {
            // Die Änderung ist angekommen; den neuen Stand schickt die Uhr dann beim nächsten Abgleich.
        }
        return sentAt
    }

    /** Unter [lock] aufrufen. */
    private fun startConfirmTimeout() {
        confirmJob?.cancel()
        confirmJob = scope.launch {
            delay(timing.confirmTimeoutMillis)
            synchronized(lock) {
                if (_settingsSend.value == SettingsSendState.AwaitingConfirmation) {
                    overlay.value = unsent
                    _settingsSend.value = SettingsSendState.Unconfirmed
                }
            }
        }
    }

    /** Ein neuer Stand der Uhr nach der letzten Änderung bestätigt sie (siehe [confirms]). */
    private fun onSnapshotAccepted(snapshot: SyncSnapshot, receivedAtMillis: Long) {
        synchronized(lock) {
            val state = _settingsSend.value
            val waiting = state == SettingsSendState.AwaitingConfirmation || state == SettingsSendState.Unconfirmed
            if (!waiting || sending || !unsent.isEmpty) return
            if (confirms(snapshot, receivedAtMillis)) confirmLocked()
        }
    }

    /**
     * Bestätigt [snapshot] die zuletzt gesendete Änderung? Er muss nach dem Senden angekommen sein und entweder schon
     * die geänderten Werte zeigen oder deutlich nach dem Senden entstanden sein (dann hat die Uhr die Änderung
     * verarbeitet, aber z. B. einen Wert begrenzt). Ein Stand, der kurz vor dem Senden entstand und die alten Werte
     * zeigt, bestätigt nichts – sonst spränge die Anzeige kurz zurück. Unter [lock] aufrufen.
     */
    private fun confirms(snapshot: SyncSnapshot, receivedAtMillis: Long): Boolean {
        if (lastSentAtMillis == 0L || receivedAtMillis < lastSentAtMillis) return false
        val matches = SettingsRules.diff(snapshot.settings, SettingsRules.apply(snapshot.settings, overlay.value)).isEmpty
        return matches || snapshot.generatedAtMillis > lastSentAtMillis + timing.clockSkewMillis
    }

    /** Unter [lock] aufrufen. */
    private fun confirmLocked() {
        overlay.value = SettingsUpdate()
        confirmJob?.cancel()
        _settingsSend.value = SettingsSendState.Confirmed(clock())
    }

    // --- Wiederherstellung ---

    /** Spielt [backup] auf die Uhr zurück; Fortschritt und Ergebnis in [restore]. */
    fun startRestore(backup: SyncBackup) {
        synchronized(lock) {
            if (_restore.value.isRunning) return
            _restore.value = RestoreState.Sending
            scope.launch { performRestore(backup) }
        }
    }

    /** Blendet ein abgeschlossenes Ergebnis aus. */
    fun clearRestoreState() {
        synchronized(lock) {
            if (!_restore.value.isRunning) _restore.value = RestoreState.Idle
        }
    }

    private val RestoreState.isRunning: Boolean
        get() = this == RestoreState.Sending || this == RestoreState.WaitingForWatch

    private suspend fun performRestore(backup: SyncBackup) {
        val restoreId = UUID.randomUUID().toString()
        val answer = CompletableDeferred<RestoreResult>()
        restoreWaiters[restoreId] = answer
        var stored = false
        var outcome: RestoreState = RestoreState.Failed(SyncProblem.Failed(statusCode = null, message = null))
        try {
            val problem = guarded {
                gateway.checkAvailability()?.let { throw SyncException(it) }
                val nodes = gateway.findWatches()
                if (nodes.withApp.isEmpty()) {
                    throw SyncException(if (nodes.connected.isEmpty()) SyncProblem.NoWatch else SyncProblem.AppMissingOnWatch)
                }
                val bytes = withContext(Dispatchers.Default) { SyncCodec.encodeBackup(backup) }
                gateway.putRestore(restoreId, bytes)
                stored = true
                _restore.value = RestoreState.WaitingForWatch
                val result = withTimeoutOrNull(timing.restoreTimeoutMillis) { answer.await() }
                when {
                    result == null -> SyncProblem.WatchDidNotAnswer
                    result.success -> {
                        outcome = RestoreState.Done(result.restoredEvents)
                        null
                    }

                    else -> SyncProblem.RejectedByWatch(result.message)
                }
            }
            if (problem != null) outcome = RestoreState.Failed(problem)
        } finally {
            // Das Ergebnis zeigen, bevor aufgeräumt wird; auch bei Abbruch bleibt nichts auf „läuft" stehen.
            _restore.value = outcome
            restoreWaiters.remove(restoreId)
            // Aufräumen, auch bei Zeitüberschreitung: Eine später doch noch zugestellte Sicherung soll die Uhr nicht
            // überraschend überschreiben, nachdem das Handy schon einen Fehler gemeldet hat.
            if (stored) {
                withContext(NonCancellable) { guarded { gateway.deleteRestore(restoreId); null } }
            }
        }
        if (outcome is RestoreState.Done) refresh()
    }

    // --- GatewayListener (Vordergrund) und Hintergrunddienst ---

    override fun onSnapshotReceived(bytes: ByteArray) {
        scope.launch { guarded { ingestSnapshot(bytes); null } }
    }

    override fun onRestoreResultReceived(bytes: ByteArray) {
        val result = SyncCodec.decodeRestoreResult(bytes) ?: return
        val waiter = restoreWaiters[result.restoreId]
        if (waiter != null) {
            waiter.complete(result)
        } else if (result.success) {
            // Antwort auf eine Wiederherstellung von vorhin (App inzwischen neu gestartet): Stand holen.
            refresh()
        }
    }

    override fun onWatchesChanged() {
        refresh()
    }

    /** Führt [block] aus und übersetzt jeden Fehler (außer Abbruch) in ein [SyncProblem]. */
    private suspend fun guarded(block: suspend () -> SyncProblem?): SyncProblem? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SyncException) {
        e.problem
    } catch (e: Exception) {
        SyncProblem.Failed(statusCode = null, message = e.message)
    }
}
