package dev.stepcron.wear.data.license

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.stepcron.shared.license.LicenseCodec
import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.LicenseRules
import dev.stepcron.shared.license.LicenseStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.licenseDataStore: DataStore<Preferences> by preferencesDataStore(name = "deskbreak_license")

/**
 * Lizenz der Uhr ([LicenseRecord]: Testbeginn, eigene Play-Abfrage, Meldung des Handys) als JSON ([LicenseCodec]) in
 * einem eigenen kleinen DataStore. Ein unlesbarer Eintrag gilt als leer – die Testphase beginnt dann neu, mehr nicht.
 */
class LicenseRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.licenseDataStore)

    val record: Flow<LicenseRecord> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.w(TAG, "Lizenz nicht lesbar – verwende den leeren Stand", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { it.toRecord() }
        .distinctUntilChanged()

    suspend fun current(): LicenseRecord = record.first()

    /** Stand der Lizenz zum Zeitpunkt [nowMillis]. */
    suspend fun status(nowMillis: Long): LicenseStatus = LicenseRules.status(current(), nowMillis)

    /** Atomares Lese-Ändere-Schreibe; liefert den gespeicherten Stand. Unverändert wird nichts geschrieben. */
    suspend fun update(transform: (LicenseRecord) -> LicenseRecord): LicenseRecord =
        dataStore.edit { prefs -> prefs[KEY_RECORD] = LicenseCodec.encodeRecord(transform(prefs.toRecord())) }.toRecord()

    private fun Preferences.toRecord(): LicenseRecord =
        this[KEY_RECORD]?.let(LicenseCodec::decodeRecord) ?: LicenseRecord()

    private companion object {
        const val TAG = "LicenseRepository"
        val KEY_RECORD = stringPreferencesKey("license_record_v1")
    }
}
