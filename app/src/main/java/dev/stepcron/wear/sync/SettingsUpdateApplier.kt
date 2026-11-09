package dev.stepcron.wear.sync

import dev.stepcron.shared.sync.SettingsUpdate
import dev.stepcron.wear.data.settings.SettingsRepository
import dev.stepcron.wear.service.ReminderEngine

/**
 * Übernimmt Einstellungen vom Handy ([SettingsUpdate], auch die einer Sicherung) über die [ReminderEngine] – wie
 * Eingaben auf der Uhr selbst, damit Alarm, Schritterkennung und Anzeigen mitziehen.
 */
class SettingsUpdateApplier(
    private val settingsRepository: SettingsRepository,
    private val engine: ReminderEngine,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Übernimmt alle Felder von [update], die gesetzt sind und sich vom gespeicherten Wert unterscheiden. Gleiche Werte
     * werden bewusst nicht erneut gesetzt: Ein zweites „Einschalten" etwa startet in der Engine einen frischen Zyklus,
     * eine unnötige Fortsetzung ebenso.
     *
     * Reihenfolge: zuerst Werte ohne Einfluss auf den Timer, dann Intervall und Wochenplan, zuletzt Schalter und
     * Pause – so startet ein Einschalten gleich mit den neuen Werten, und eine Pause überlebt das Einschalten (das eine
     * Pause sonst beendet).
     *
     * @return ob sich etwas geändert hat.
     */
    suspend fun apply(update: SettingsUpdate): Boolean {
        val change = SyncMapping.toSettingsChange(update)
        val current = settingsRepository.currentSettings()
        var changed = false

        suspend fun <T : Any> changeIfDifferent(value: T?, currentValue: T, set: suspend (T) -> Unit) {
            if (value == null || value == currentValue) return
            set(value)
            changed = true
        }

        changeIfDifferent(change.snoozeMinutes, current.snoozeMinutes) { engine.setSnoozeMinutes(it) }
        changeIfDifferent(change.stepThreshold, current.stepThreshold) { engine.setStepThreshold(it) }
        changeIfDifferent(change.vibration, current.vibration) { engine.setVibration(it) }
        changeIfDifferent(change.respectQuietModes, current.respectQuietModes) { engine.setRespectQuietModes(it) }
        changeIfDifferent(change.onlyWhenWorn, current.onlyWhenWorn) { engine.setOnlyWhenWorn(it) }
        changeIfDifferent(change.dailyGoal, current.dailyGoal) { engine.setDailyGoal(it) }
        changeIfDifferent(change.autoStepEnabled, current.autoStepEnabled) { engine.setAutoStepEnabled(it) }
        changeIfDifferent(change.intervalMinutes, current.intervalMinutes) { engine.setIntervalMinutes(it) }
        changeIfDifferent(change.schedule, current.schedule) { schedule -> engine.updateSchedule { schedule } }
        changeIfDifferent(change.enabled, current.enabled) { engine.setEnabled(it) }

        // Ausgeschaltet gibt es keine Pause (das Ausschalten beendet sie ohnehin).
        val pausedUntilMillis = change.pausedUntilMillis
        if (pausedUntilMillis != null && (change.enabled ?: current.enabled) && applyPause(pausedUntilMillis)) {
            changed = true
        }
        return changed
    }

    private suspend fun applyPause(pausedUntilMillis: Long): Boolean {
        // Frisch lesen: Ein Einschalten eben hat eine gespeicherte Pause bereits beendet.
        val pausedUntil = settingsRepository.currentSettings().pausedUntil
        return when (val pause = SyncMapping.toPauseChange(pausedUntilMillis, clock())) {
            PauseChange.Resume -> {
                if (pausedUntil == 0L) return false
                engine.resume()
                true
            }

            is PauseChange.PauseUntil -> {
                if (pause.untilMillis == pausedUntil) return false
                engine.pauseUntil(pause.untilMillis)
                true
            }
        }
    }
}
