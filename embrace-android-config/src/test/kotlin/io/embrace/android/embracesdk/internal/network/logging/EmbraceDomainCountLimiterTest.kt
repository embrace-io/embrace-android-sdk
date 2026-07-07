package io.embrace.android.embracesdk.internal.network.logging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class EmbraceDomainCountLimiterTest {

    @Test
    fun `unconfigured domains each get their own budget at the default limit`() {
        val limiter = createLimiter(default = 2)

        assertTrue(limiter.canLogNetworkRequest("example.com"))
        assertTrue(limiter.canLogNetworkRequest("example.com"))
        assertFalse(limiter.canLogNetworkRequest("example.com"))

        // a different unconfigured domain is counted independently
        assertTrue(limiter.canLogNetworkRequest("other.com"))
        assertTrue(limiter.canLogNetworkRequest("other.com"))
        assertFalse(limiter.canLogNetworkRequest("other.com"))
    }

    @Test
    fun `all ip address requests share a single budget at the default limit`() {
        val limiter = createLimiter(default = 2)

        assertTrue(limiter.canLogNetworkRequest("1.1.1.1"))
        assertTrue(limiter.canLogNetworkRequest("2.2.2.2"))
        assertFalse(limiter.canLogNetworkRequest("3.3.3.3"))
    }

    @Test
    fun `a configured suffix budget is shared across matching subdomains`() {
        val limiter = createLimiter(
            default = 100,
            limits = mapOf(
                ".example.com" to 2,
            ),
        )

        assertTrue(limiter.canLogNetworkRequest("api.example.com"))
        assertTrue(limiter.canLogNetworkRequest("cdn.example.com"))
        // the shared .example.com budget is now exhausted for every subdomain
        assertFalse(limiter.canLogNetworkRequest("media.example.com"))
    }

    @Test
    fun `a specific budget is protected even when the broader budget is exhausted by others`() {
        val limiter = createLimiter(
            default = 100,
            limits = mapOf(
                "limited.org" to 2,
                ".org" to 2,
            ),
        )

        // other .org traffic exhausts the shared .org budget
        assertTrue(limiter.canLogNetworkRequest("foo.org"))
        assertTrue(limiter.canLogNetworkRequest("foo.org"))
        assertFalse(limiter.canLogNetworkRequest("foo.org"))

        // limited.org still has its own protected budget of 2
        assertTrue(limiter.canLogNetworkRequest("limited.org"))
        assertTrue(limiter.canLogNetworkRequest("limited.org"))
        // own budget and the shared .org budget are both full now, so no overflow is possible
        assertFalse(limiter.canLogNetworkRequest("limited.org"))
    }

    @Test
    fun `reset clears counts and re-reads the suppliers`() {
        var defaultLimit = 1
        var limits = emptyMap<String, Int>()
        val limiter = EmbraceDomainCountLimiter(
            defaultLimitSupplier = { defaultLimit },
            domainLimitsSupplier = { limits },
        )

        assertTrue(limiter.canLogNetworkRequest("a.com"))
        assertFalse(limiter.canLogNetworkRequest("a.com"))

        // change configuration and reset
        defaultLimit = 2
        limits = mapOf(".io" to 1)
        limiter.reset()

        // counts are cleared and the new default limit is in effect
        assertTrue(limiter.canLogNetworkRequest("a.com"))
        assertTrue(limiter.canLogNetworkRequest("a.com"))
        assertFalse(limiter.canLogNetworkRequest("a.com"))

        // the newly configured suffix rule now applies
        assertTrue(limiter.canLogNetworkRequest("x.io"))
        assertFalse(limiter.canLogNetworkRequest("x.io"))
    }

    private fun createLimiter(
        default: Int = 100,
        limits: Map<String, Int> = emptyMap(),
    ) = EmbraceDomainCountLimiter(
        defaultLimitSupplier = { default },
        domainLimitsSupplier = { limits },
    )
}
