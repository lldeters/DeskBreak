package dev.stepcron.shared.sync

/**
 * Deep Links der Handy-App. Die Uhr öffnet sie über `RemoteActivityHelper` auf dem gekoppelten Handy, sofern dort
 * DeskBreak installiert ist (Capability [SyncPaths.CAPABILITY_PHONE]); sonst die Seite im Play Store.
 */
object DeepLinks {
    const val SCHEME = "deskbreak"

    const val HOST_UNLOCK = "unlock"

    /** Öffnet in der Handy-App die Seite „DeskBreak freischalten". */
    const val UNLOCK = "$SCHEME://$HOST_UNLOCK"
}
