package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.createNetworkBehavior
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.config.LocalConfigParser
import io.embrace.android.embracesdk.internal.config.local.DomainLocalConfig
import io.embrace.android.embracesdk.internal.config.local.NetworkLocalConfig
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.opentelemetry.OpenTelemetryConfiguration
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class NetworkBehaviorImplTest {

    companion object {
        private const val testCleanPublicKey =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQE" +
                "AuAZAv5tzK9Ab/DsVpNaYiuslKQsOHjz4N4haZLT8VaVIrlVjtkd5nPrVgEKStQf6PKn" +
                "Q+1C0Tp069b6aPUkG22UL96nCKQ1eCIwRUT+Da7ac2YVuL21+HTs1KxLEWgN7qGy1uYN" +
                "onrpsiY3XqzDvYMo65oFzbBV+yctuGHDFaulULJiLL8cE3/Rg3T0RfHK+C5/PqC8FBj6" +
                "kn3FP9FZJM4cty3nzbNWknj8r7+ikmOwma6CHEZz2u1gwPhIchNxNKuUF+4vxcBre9V/" +
                "96LYOjSOGSDJmJN6ehUJjUpu7YSuGCki8YoLHAyoD/mYy7N/hYSeZwHiNjM+r44lZHNQ" +
                "TpwIDAQAB"
    }

    private val local = SdkLocalConfig(
        networking = NetworkLocalConfig(
            traceIdHeader = "x-custom-trace",
            captureRequestContentLength = true,
            enableNativeMonitoring = false,
            domains = listOf(
                DomainLocalConfig(
                    "google.com",
                    100
                )
            ),
            disabledUrlPatterns = listOf("google.com"),
            defaultCaptureLimit = 720,
        ),
        capturePublicKey = "test"
    )

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
        with(createNetworkBehavior(localCfg = { null }, remoteCfg = { null })) {
            assertEquals("x-emb-trace-id", getTraceIdHeader())
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
    fun testLocalOnly() {
        with(createNetworkBehavior(localCfg = { local }, remoteCfg = { null })) {
            assertEquals("x-custom-trace", getTraceIdHeader())
            assertTrue(isRequestContentLengthCaptureEnabled())
            assertFalse(isHttpUrlConnectionCaptureEnabled())
            assertEquals(mapOf("google.com" to 100), getLimitsByDomain())
            assertEquals(720, getRequestLimitPerDomain())
            assertFalse(isUrlEnabled("google.com"))
            assertTrue(isCaptureBodyEncryptionEnabled())
            assertEquals("test", getNetworkBodyCapturePublicKey())
        }
    }

    @Test
    fun testRemoteOnly() {
        with(createNetworkBehavior(localCfg = { null }, remoteCfg = { remote })) {
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
    fun testRemoteAndLocal() {
        with(createNetworkBehavior(localCfg = { local }, remoteCfg = { remote })) {
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
        val cfg = SdkLocalConfig(
            networking = NetworkLocalConfig(
                disabledUrlPatterns = listOf("a.b.c", "invalid[}regex")
            )
        )
        with(createNetworkBehavior(localCfg = { cfg })) {
            assertTrue(isUrlEnabled("invalid[}regex"))
        }
    }

    @Test
    fun testGetNetworkBodyCapturePublicKey() {
        val otelCfg = OpenTelemetryConfiguration(
            SpanSinkImpl(),
            LogSinkImpl(),
            SystemInfo()
        )
        val json = ResourceReader.readResourceAsText("public_key_config.json")
        val localConfig = LocalConfigParser.buildConfig("aaa", false, json, EmbraceSerializer(), otelCfg, EmbLoggerImpl())
        val behavior = createNetworkBehavior(localCfg = localConfig::sdkConfig)
        assertEquals(testCleanPublicKey, behavior.getNetworkBodyCapturePublicKey())
    }
}
