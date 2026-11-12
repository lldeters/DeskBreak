package dev.stepcron.wear.ui.license

import com.google.android.gms.wearable.Node
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkFallbacksTest {

    /** Zeichnet die Versuche auf; jeder liefert das vorgegebene Ergebnis. */
    private val attempts = mutableListOf<String>()

    private fun attempt(name: String, result: Boolean): Boolean {
        attempts += name
        return result
    }

    private fun unlock(inApp: Boolean, store: Boolean) = runBlocking {
        LinkFallbacks.unlockOnPhone(
            openInPhoneApp = { attempt("app", inApp) },
            openStoreOnPhone = { attempt("store", store) },
        )
    }

    private fun manage(phone: Boolean, watch: Boolean) = runBlocking {
        LinkFallbacks.manageSubscription(
            openOnPhone = { attempt("phone", phone) },
            openOnWatch = { attempt("watch", watch) },
        )
    }

    @Test
    fun `unlock opens the phone app when it is reachable, without google play`() {
        assertEquals(PhoneLinkState.OPENED_IN_APP, unlock(inApp = true, store = true))
        assertEquals(listOf("app"), attempts)
    }

    @Test
    fun `without the phone app unlock falls back to google play on the phone`() {
        assertEquals(PhoneLinkState.OPENED_ON_PHONE, unlock(inApp = false, store = true))
        assertEquals(listOf("app", "store"), attempts)
    }

    @Test
    fun `unlock fails when the phone opens neither`() {
        assertEquals(PhoneLinkState.FAILED, unlock(inApp = false, store = false))
        assertEquals(listOf("app", "store"), attempts)
    }

    @Test
    fun `manage subscription opens google play on the phone, not on the watch`() {
        assertEquals(PhoneLinkState.OPENED_ON_PHONE, manage(phone = true, watch = true))
        assertEquals(listOf("phone"), attempts)
    }

    @Test
    fun `without an android phone manage subscription opens on the watch`() {
        assertEquals(PhoneLinkState.OPENED_ON_WATCH, manage(phone = false, watch = true))
        assertEquals(listOf("phone", "watch"), attempts)
    }

    @Test
    fun `manage subscription fails when neither phone nor watch can open it`() {
        val state = manage(phone = false, watch = false)

        assertEquals(PhoneLinkState.FAILED, state)
        assertEquals(listOf("phone", "watch"), attempts)
    }

    @Test
    fun `the way without a phone is shown only when no phone opened the page`() {
        val withoutPhone = PhoneLinkState.entries.filter { it.withoutPhone }

        assertEquals(listOf(PhoneLinkState.OPENED_ON_WATCH, PhoneLinkState.FAILED), withoutPhone)
    }

    @Test
    fun `nearby phones come first, the order stays otherwise`() {
        val nodes = listOf(node("cloud-a", nearby = false), node("bt", nearby = true), node("cloud-b", nearby = false))

        assertEquals(listOf("bt", "cloud-a", "cloud-b"), LinkFallbacks.phoneAppNodeIds(nodes))
        assertEquals(emptyList<String>(), LinkFallbacks.phoneAppNodeIds(emptyList()))
    }

    private fun node(id: String, nearby: Boolean) = object : Node {
        override fun getDisplayName() = "Pixel"
        override fun getId() = id
        override fun isNearby() = nearby
    }
}
