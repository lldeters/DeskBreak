package dev.stepcron.mobile.sync

/**
 * Warum ein Abgleich, eine Änderung oder eine Wiederherstellung nicht (ganz) geklappt hat. Jeder Fehler der
 * Play Services landet hier und wird angezeigt – nie als Absturz.
 */
sealed interface SyncProblem {
    /** Google Play-Dienste fehlen, sind deaktiviert oder zu alt. */
    data object PlayServicesMissing : SyncProblem

    /** Die Wearable-API ist auf dem Handy nicht verfügbar (Begleit-App der Uhr fehlt). */
    data object WearApiUnavailable : SyncProblem

    /** Keine Uhr verbunden. */
    data object NoWatch : SyncProblem

    /** Eine Uhr ist verbunden, aber DeskBreak ist dort nicht installiert. */
    data object AppMissingOnWatch : SyncProblem

    /** Die Uhr ist erreichbar, hat aber noch keinen Stand veröffentlicht. */
    data object NoDataYet : SyncProblem

    /** Die Anfrage ging raus, ein neuer Stand kam aber nicht an. */
    data object WatchDidNotAnswer : SyncProblem

    /** Die Daten der Uhr haben ein neueres Format – die Handy-App muss aktualisiert werden. */
    data class DataTooNew(val version: Int) : SyncProblem

    /** Die Daten der Uhr ließen sich nicht lesen. */
    data object InvalidData : SyncProblem

    /** Die Play Services haben nicht rechtzeitig geantwortet. */
    data object Timeout : SyncProblem

    /** Die Uhr hat die Wiederherstellung abgelehnt; [message] kommt von der Uhr. */
    data class RejectedByWatch(val message: String?) : SyncProblem

    /** Sonstiger Fehler der Play Services ([statusCode] einer ApiException, falls vorhanden). */
    data class Failed(val statusCode: Int?, val message: String?) : SyncProblem
}

/** Fehler eines Data-Layer-Aufrufs, bereits in ein [SyncProblem] übersetzt. */
class SyncException(val problem: SyncProblem, cause: Throwable? = null) : Exception(problem.toString(), cause)
