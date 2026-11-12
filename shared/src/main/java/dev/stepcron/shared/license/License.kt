package dev.stepcron.shared.license

import kotlinx.serialization.Serializable

/**
 * Lizenzmodell von DeskBreak (Uhr und Handy gleich):
 *
 * - Ab der ersten Nutzung auf einem der beiden Geräte läuft eine kostenlose Testphase von [LicenseRules.TRIAL_DAYS]
 *   Tagen – ohne Zahlungsdaten, von der App selbst verwaltet.
 * - Danach schaltet man über Google Play frei: einmalig ([Unlock.LIFETIME]) oder per Monatsabo
 *   ([Unlock.SUBSCRIPTION]). Uhr und Handy haben dieselbe applicationId, ein Kauf gilt also für beide, solange
 *   dasselbe Google-Konto angemeldet ist. Zusätzlich meldet jedes Gerät dem anderen, was es selbst bei Play
 *   gefunden hat ([LicenseInfo]) – so reicht es, wenn eines der beiden Geräte den Kauf sieht.
 * - Ohne Freischaltung nach der Testphase ([LicenseStatus.Expired]) erinnert die Uhr nicht mehr; Verlauf, Export
 *   und Sicherung bleiben nutzbar (es sind die Daten der Nutzerin bzw. des Nutzers).
 */
object LicenseProducts {
    /** Einmalkauf (Play Console: In-App-Produkt, nicht verbrauchbar), z. B. 5 €. */
    const val LIFETIME = "deskbreak_lifetime"

    /** Abo (Play Console: Abo mit einem Basis-Abo [MONTHLY_BASE_PLAN], monatlich verlängert), z. B. 1 €/Monat. */
    const val SUBSCRIPTION = "deskbreak_monthly"

    /** Basis-Abo-ID des Abos [SUBSCRIPTION]. */
    const val MONTHLY_BASE_PLAN = "monthly"

    fun productId(unlock: Unlock): String = when (unlock) {
        Unlock.LIFETIME -> LIFETIME
        Unlock.SUBSCRIPTION -> SUBSCRIPTION
    }

    fun unlockFor(productId: String): Unlock? = when (productId) {
        LIFETIME -> Unlock.LIFETIME
        SUBSCRIPTION -> Unlock.SUBSCRIPTION
        else -> null
    }
}

/** Art der Freischaltung. Gibt es beide, zählt [LIFETIME]. */
enum class Unlock {
    LIFETIME,
    SUBSCRIPTION;

    companion object {
        fun fromName(name: String?): Unlock? = entries.firstOrNull { it.name == name }

        /** Die „beste" Freischaltung aus [unlocks]: [LIFETIME] vor [SUBSCRIPTION]. */
        fun best(unlocks: Collection<Unlock>): Unlock? = when {
            LIFETIME in unlocks -> LIFETIME
            SUBSCRIPTION in unlocks -> SUBSCRIPTION
            else -> null
        }
    }
}

/**
 * Was ein Gerät über die Lizenz weiß – gespeichert auf jedem Gerät (z. B. als JSON über [LicenseCodec]).
 *
 * [ownUnlock] stammt aus der Play-Abfrage dieses Geräts, [peerUnlock] ist das, was das andere Gerät zuletzt
 * gemeldet hat. Beide nur so frisch wie ihr `CheckedAtMillis`; ein Abo zählt nur [LicenseRules.SUBSCRIPTION_TRUST_MILLIS]
 * lang ohne neue Bestätigung (ein gekündigtes Abo soll nicht für immer gelten, nur weil ein Gerät offline ist).
 */
@Serializable
data class LicenseRecord(
    /** Beginn der Testphase (Epoch-Millis); 0 = noch nicht begonnen. Über beide Geräte das früheste Datum. */
    val trialStartMillis: Long = 0L,
    /** Name von [Unlock] laut Play-Abfrage auf diesem Gerät; `null` = keine Freischaltung gefunden. */
    val ownUnlock: String? = null,
    /** Zeitpunkt der letzten erfolgreichen Play-Abfrage auf diesem Gerät; 0 = noch nie. */
    val ownCheckedAtMillis: Long = 0L,
    /** Name von [Unlock], den das andere Gerät zuletzt gemeldet hat. */
    val peerUnlock: String? = null,
    /** Wann das andere Gerät seine Freischaltung zuletzt bei Play bestätigt hat (laut seiner Meldung). */
    val peerCheckedAtMillis: Long = 0L,
)

/** Meldung an das andere Gerät: die eigene Sicht (Testbeginn und eigene Play-Abfrage). */
@Serializable
data class LicenseInfo(
    val trialStartMillis: Long = 0L,
    /** Name von [Unlock] laut Play-Abfrage des meldenden Geräts; `null` = keine. */
    val unlock: String? = null,
    val checkedAtMillis: Long = 0L,
)

/** Stand der Lizenz zu einem Zeitpunkt. */
sealed interface LicenseStatus {

    /** Darf DeskBreak erinnern? (Testphase oder freigeschaltet.) */
    val isActive: Boolean

    /** Testphase läuft bis [endsAtMillis]; [daysLeft] ist aufgerundet (letzter Tag: 1). */
    data class Trial(val endsAtMillis: Long, val daysLeft: Int) : LicenseStatus {
        override val isActive: Boolean get() = true
    }

    /** Testphase vorbei (seit [endedAtMillis]), keine gültige Freischaltung. */
    data class Expired(val endedAtMillis: Long) : LicenseStatus {
        override val isActive: Boolean get() = false
    }

    data class Unlocked(val unlock: Unlock) : LicenseStatus {
        override val isActive: Boolean get() = true
    }
}
