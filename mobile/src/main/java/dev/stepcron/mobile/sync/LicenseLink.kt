package dev.stepcron.mobile.sync

import dev.stepcron.shared.license.LicenseInfo

/**
 * Verbindet den Abgleich mit der Lizenz (umgesetzt von dev.stepcron.mobile.license.LicenseRepository): Was die Uhr
 * im Snapshot meldet, geht hierher; was das Handy meldet, schickt der Abgleich vor jeder Bitte um einen frischen
 * Stand mit – so passt der Stand der Uhr schon dazu.
 */
interface LicenseLink {
    /** Lizenz-Sicht der Uhr aus einem gelesenen Snapshot übernehmen. */
    suspend fun onWatchLicense(info: LicenseInfo)

    /** Lizenz-Sicht des Handys für die Uhr; `null`, solange sie noch nicht feststeht. */
    suspend fun licenseForWatch(): LicenseInfo?
}
