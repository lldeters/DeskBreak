package dev.stepcron.mobile.ui.settings

import android.net.Uri
import dev.stepcron.mobile.settings.VibrationLevel

/**
 * Alles, was der Einstellungs-Bildschirm auslösen kann. Das [dev.stepcron.mobile.ui.AppViewModel] setzt es um;
 * Screenshots und Vorschauen kommen mit einer leeren Umsetzung aus. Jede Änderung geht sofort (kurz gesammelt)
 * an die Uhr.
 */
interface SettingsActions {
    fun syncNow()

    /** Sendet eine fehlgeschlagene Änderung noch einmal. */
    fun retrySettings()

    // --- Erinnerungen ---
    fun setEnabled(enabled: Boolean)
    fun stepInterval(steps: Int)
    fun stepDailyGoal(steps: Int)

    // --- Pause ---
    fun pauseOneHour()
    fun pauseRestOfDay()
    fun endPause()

    // --- Wochenplan ---
    fun setDayEnabled(dayIndex: Int, enabled: Boolean)

    /** Beginn und Ende eines Tages (Minuten seit Mitternacht, Ende bis 1440). */
    fun setDayWindow(dayIndex: Int, startMinute: Int, endMinute: Int)

    /** Dieselben Uhrzeiten für alle Tage; aktive Tage bleiben aktiv, freie frei. */
    fun setWindowForAllDays(startMinute: Int, endMinute: Int)

    // --- Auf der Uhr ---
    fun stepSnooze(steps: Int)
    fun setVibration(level: VibrationLevel)
    fun setRespectQuietModes(enabled: Boolean)
    fun setOnlyWhenWorn(enabled: Boolean)

    // --- Schritterkennung ---
    fun setAutoSteps(enabled: Boolean)
    fun stepStepThreshold(steps: Int)

    // --- Daten (die Dateiauswahl startet der Bildschirm, die Ergebnisse gehen hierher) ---
    fun csvFileName(): String
    fun backupFileName(): String
    fun exportCsv(uri: Uri)
    fun exportBackup(uri: Uri)

    /** Liest eine Sicherung; bei Erfolg folgt die Rückfrage (AppUiState.local.pendingRestore). */
    fun loadBackup(uri: Uri)
    fun confirmRestore()
    fun cancelRestore()
    fun dismissRestoreResult()

    /** Keine App zum Speichern bzw. Öffnen von Dateien gefunden. */
    fun onNoFileApp()
}
