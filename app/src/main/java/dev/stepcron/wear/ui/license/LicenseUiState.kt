package dev.stepcron.wear.ui.license

import dev.stepcron.billing.BillingAvailability
import dev.stepcron.billing.BillingOffer
import dev.stepcron.billing.BillingProblem
import dev.stepcron.billing.BillingState
import dev.stepcron.shared.license.LicenseRecord
import dev.stepcron.shared.license.LicenseRules
import dev.stepcron.shared.license.LicenseStatus
import dev.stepcron.shared.license.Unlock

/** Wie weit das Öffnen von Google Play auf dem Handy ist. */
enum class PhoneLinkState {
    IDLE,
    OPENING,

    /** Auf dem Handy geöffnet – dort geht es weiter. */
    OPENED,

    /** Kein Handy erreichbar (nicht verbunden, kein Android-Handy). */
    FAILED,
}

/** Hinweis zum Kauf. */
enum class LicenseNotice {
    /** Kauf begonnen, Zahlung steht aus. */
    PENDING,

    /** Google Play gerade nicht erreichbar. */
    UNREACHABLE,

    /** Kauf aus einem anderen Grund nicht möglich. */
    FAILED,
}

/** Was der Bildschirm „Freischaltung" zeigt. */
data class LicenseUiState(
    val loading: Boolean = true,
    val status: LicenseStatus? = null,
    /**
     * Direkt auf der Uhr kaufbar, Einmalkauf zuerst. Leer, wenn Play Billing hier nicht bereit ist (dann bleibt das
     * Handy) oder schon freigeschaltet ist.
     */
    val offers: List<BillingOffer> = emptyList(),
    /** Die Käufe werden gerade bei Play abgefragt. */
    val checking: Boolean = false,
    val phoneLink: PhoneLinkState = PhoneLinkState.IDLE,
    val notice: LicenseNotice? = null,
) {
    /** Die gültige Freischaltung; `null` in der Testphase und danach. */
    val unlock: Unlock? get() = (status as? LicenseStatus.Unlocked)?.unlock

    companion object {
        fun create(
            record: LicenseRecord,
            nowMillis: Long,
            billing: BillingState,
            checking: Boolean,
            phoneLink: PhoneLinkState,
        ): LicenseUiState {
            val status = LicenseRules.status(record, nowMillis)
            val unlocked = status is LicenseStatus.Unlocked
            val offers = if (!unlocked && billing.availability == BillingAvailability.READY) {
                listOfNotNull(billing.offers[Unlock.LIFETIME], billing.offers[Unlock.SUBSCRIPTION])
            } else {
                emptyList()
            }
            val notice = when {
                unlocked -> null
                billing.pending.isNotEmpty() -> LicenseNotice.PENDING
                else -> noticeFor(billing.problem)
            }
            return LicenseUiState(
                loading = false,
                status = status,
                offers = offers,
                checking = checking,
                phoneLink = phoneLink,
                notice = notice,
            )
        }

        /**
         * Hinweis zu [problem]. Kein Play Billing auf der Uhr ist kein Fehler (viele Uhren können das nicht – dann über
         * das Handy), „bereits gekauft" klärt das Gateway selbst.
         */
        private fun noticeFor(problem: BillingProblem?): LicenseNotice? = when (problem) {
            null, BillingProblem.BILLING_UNAVAILABLE, BillingProblem.ALREADY_OWNED -> null
            BillingProblem.NETWORK, BillingProblem.SERVICE_UNAVAILABLE -> LicenseNotice.UNREACHABLE
            BillingProblem.PRODUCT_UNAVAILABLE, BillingProblem.DEVELOPER_ERROR, BillingProblem.UNKNOWN -> LicenseNotice.FAILED
        }
    }
}
