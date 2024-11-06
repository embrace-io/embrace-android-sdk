package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createNetworkBehavior
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class NetworkBehaviorImplTest {

    private val remote = RemoteConfig(
        networkConfig = NetworkRemoteConfig(
            defaultCaptureLimit = 409,
            domainLimits = mapOf(
                "google.com" to 50
            )
        ),
        disabledUrlPatterns = setOf("example.com"),
        networkCaptureRules = setOf(
            NetworkCaptureRuleRemoteConfig(
                "test",
                5000,
                "GET",
                "google.com",
            )
        )
    )

    @Test
    fun testDefaults() {
        with(createNetworkBehavior(remoteCfg = { null })) {
            assertFalse(isRequestContentLengthCaptureEnabled())
            assertTrue(isHttpUrlConnectionCaptureEnabled())
            assertEquals(1000, getRequestLimitPerDomain())
            assertEquals(emptyMap<String, Int>(), getLimitsByDomain())
            assertTrue(isUrlEnabled("google.com"))
            assertFalse(isCaptureBodyEncryptionEnabled())
            assertNull(getNetworkBodyCapturePublicKey())
            assertEquals(emptySet<NetworkCaptureRuleRemoteConfig>(), getNetworkCaptureRules())
        }
    }

    @Test
    fun testRemoteOnly() {
        with(createNetworkBehavior(remoteCfg = { remote })) {
            assertEquals(409, getRequestLimitPerDomain())
            assertEquals(mapOf("google.com" to 50), getLimitsByDomain())
            assertTrue(isUrlEnabled("google.com"))
            assertFalse(isUrlEnabled("example.com"))
            assertEquals(
                NetworkCaptureRuleRemoteConfig(
                    "test",
                    5000,
                    "GET",
                    "google.com",
                ),
                getNetworkCaptureRules().single()
            )
        }
    }

    @Test
    fun testNetworkingInvalidDisabledRegexIgnored() {
        with(createNetworkBehavior(disabledUrlPatterns = listOf("a.b.c", "invalid[}regex"))) {
            assertTrue(isUrlEnabled("invalid[}regex"))
        }
    }
}
