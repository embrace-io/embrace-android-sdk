package io.embrace.android.embracesdk.internal.network.logging

import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkBehavior
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceDomainCountLimiterTest {

    private lateinit var configService: FakeConfigService
    private lateinit var domainCountLimiter: EmbraceDomainCountLimiter

    @Before
    fun setUp() {
        configService = FakeConfigService(networkBehavior = FakeNetworkBehavior())
        createDomainCountLimiter()
    }

    private fun createDomainCountLimiter() {
        domainCountLimiter = EmbraceDomainCountLimiter(configService)
    }

    @Test
    fun `test implicit default network call limits`() {
        var canLogRequest1 = false
        repeat(1005) {
            canLogRequest1 = domainCountLimiter.canLogNetworkRequest("www.overLimit1.com")
        }
        val canLogRequest2 = domainCountLimiter.canLogNetworkRequest("www.overLimit2.com")

        assertTrue(canLogRequest2)
        assertFalse(canLogRequest1)
    }

    @Test
    fun `test domain specific local limits`() {
        // given a domain with a limit of 2
        configService.networkBehavior = FakeNetworkBehavior(domains = mapOf("overLimit1.com" to 2))
        createDomainCountLimiter()

        // logging 2 requests is fine
        repeat(2) {
            assertTrue(domainCountLimiter.canLogNetworkRequest("www.overLimit1.com"))
        }

        // logging another one is not
        assertFalse(domainCountLimiter.canLogNetworkRequest("www.overLimit1.com"))
    }

    @Test
    fun `test default local limits`() {
        // given a default local limit of 2
        configService.networkBehavior = FakeNetworkBehavior(2)

        createDomainCountLimiter()

        // logging 2 requests is fine
        repeat(2) {
            assertTrue(domainCountLimiter.canLogNetworkRequest("www.overLimit1.com"))
        }

        // logging another one is not
        assertFalse(domainCountLimiter.canLogNetworkRequest("www.overLimit1.com"))

        // logging another domain is okay
        assertTrue(domainCountLimiter.canLogNetworkRequest("www.overLimit2.com"))
    }

    @Test
    fun `test local limits with default and domain specific limits`() {
        // given a default local limit of 2 and a domain specific limit of 3
        configService.networkBehavior = FakeNetworkBehavior(
            captureLimit = 2,
            domains = mapOf("overLimit1.com" to 3)
        )

        createDomainCountLimiter()

        // logging 3 requests of the domain with a specific limit is fine
        repeat(3) {
            assertTrue(domainCountLimiter.canLogNetworkRequest("www.overLimit1.com"))
        }

        // logging another one is not
        assertFalse(domainCountLimiter.canLogNetworkRequest("www.overLimit1.com"))

        // logging 2 requests of any domain is fine
        repeat(2) {
            assertTrue(domainCountLimiter.canLogNetworkRequest("www.overLimit2.com"))
        }

        // logging another one is not
        assertFalse(domainCountLimiter.canLogNetworkRequest("www.overLimit2.com"))
    }

    @Test
    fun `test explicit remote limits as a ceiling for local limits`() {
        // given a local and a remote limit of different values, both for default and specific domains
        configService.networkBehavior = FakeNetworkBehavior(
            captureLimit = 5,
            domains = mapOf("limited.org" to 10)
        )

        createDomainCountLimiter()

        // logging 10 requests of the domain with a specific limit is fine
        repeat(10) {
            assertTrue(domainCountLimiter.canLogNetworkRequest("www.limited.org"))
        }

        // logging another one is not
        assertFalse(domainCountLimiter.canLogNetworkRequest("www.limited.org"))

        // logging 5 requests of any other domain is fine
        repeat(5) {
            assertTrue(domainCountLimiter.canLogNetworkRequest("www.another.com"))
        }

        // logging another one is not
        assertFalse(domainCountLimiter.canLogNetworkRequest("www.another.com"))
    }

    @Test
    fun `test implicit remote limit as a ceiling for local limit`() {
        configService.networkBehavior = FakeNetworkBehavior(
            captureLimit = 1000,
            domains = mapOf("limited.org" to 1000)
        )
        createDomainCountLimiter()

        // logging 1000 requests of the domain with a specific limit is fine
        repeat(1000) {
            assertTrue(domainCountLimiter.canLogNetworkRequest("www.limited.org"))
        }

        // logging another one is not, because the implicit remote limit is 1000
        assertFalse(domainCountLimiter.canLogNetworkRequest("www.limited.org"))
    }

    @Test
    fun `limit applies to all domains with a given suffix`() {
        // given a specific domain with a limit of 4
        configService.networkBehavior = FakeNetworkBehavior(
            captureLimit = 3,
            domains = mapOf("limited.org" to 4)
        )

        createDomainCountLimiter()

        // logging different domains with the same suffix 4 times is fine
        repeat(2) {
            assertTrue(domainCountLimiter.canLogNetworkRequest("www.limited.org"))
            assertTrue(domainCountLimiter.canLogNetworkRequest("admin.limited.org"))
        }
        // logging another one is not
        assertFalse(domainCountLimiter.canLogNetworkRequest("verylimited.org"))

        // logging with a different suffix is fine
        assertTrue(domainCountLimiter.canLogNetworkRequest("admin.limited.com"))
    }

    @Test
    fun `clearing service resets the limit`() {
        // given a default limit of 5
        configService.networkBehavior = FakeNetworkBehavior(5)
        createDomainCountLimiter()

        // logging 5 requests is fine
        repeat(5) {
            assertTrue(domainCountLimiter.canLogNetworkRequest("www.overLimit1.com"))
        }

        // logging another one is not
        assertFalse(domainCountLimiter.canLogNetworkRequest("www.overLimit1.com"))

        // when the service is cleared
        domainCountLimiter.cleanCollections()

        // logging 5 requests is fine again
        repeat(5) {
            assertTrue(domainCountLimiter.canLogNetworkRequest("www.overLimit1.com"))
        }

        // logging another one is not
        assertFalse(domainCountLimiter.canLogNetworkRequest("www.overLimit1.com"))
    }
}
