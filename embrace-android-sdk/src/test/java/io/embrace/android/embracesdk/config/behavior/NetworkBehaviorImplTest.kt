package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.fakeNetworkBehavior
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.config.LocalConfigParser
import io.embrace.android.embracesdk.internal.config.local.DomainLocalConfig
import io.embrace.android.embracesdk.internal.config.local.NetworkLocalConfig
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.opentelemetry.OpenTelemetryConfiguration
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
        with(fakeNetworkBehavior(localCfg = { null }, remoteCfg = { null })) {
            assertEquals("x-emb-trace-id", getTraceIdHeader())
            assertFalse(isRequestContentLengthCaptureEnabled())
            assertTrue(isNativeNetworkingMonitoringEnabled())
            assertEquals(1000, getNetworkCaptureLimit())
            assertEquals(emptyMap<String, Int>(), getNetworkCallLimitsPerDomainSuffix())
            assertTrue(isUrlEnabled("google.com"))
            assertFalse(isCaptureBodyEncryptionEnabled())
            assertNull(getCapturePublicKey())
            assertEquals(emptySet<NetworkCaptureRuleRemoteConfig>(), getNetworkCaptureRules())
        }
    }

    @Test
    fun testLocalOnly() {
        with(fakeNetworkBehavior(localCfg = { local }, remoteCfg = { null })) {
            assertEquals("x-custom-trace", getTraceIdHeader())
            assertTrue(isRequestContentLengthCaptureEnabled())
            assertFalse(isNativeNetworkingMonitoringEnabled())
            assertEquals(mapOf("google.com" to 100), getNetworkCallLimitsPerDomainSuffix())
            assertEquals(720, getNetworkCaptureLimit())
            assertFalse(isUrlEnabled("google.com"))
            assertTrue(isCaptureBodyEncryptionEnabled())
            assertEquals("test", getCapturePublicKey())
        }
    }

    @Test
    fun testRemoteOnly() {
        with(fakeNetworkBehavior(localCfg = { null }, remoteCfg = { remote })) {
            assertEquals(409, getNetworkCaptureLimit())
            assertEquals(mapOf("google.com" to 50), getNetworkCallLimitsPerDomainSuffix())
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
        with(fakeNetworkBehavior(localCfg = { local }, remoteCfg = { remote })) {
            assertEquals(409, getNetworkCaptureLimit())
            assertEquals(mapOf("google.com" to 50), getNetworkCallLimitsPerDomainSuffix())
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
        with(fakeNetworkBehavior(localCfg = { cfg })) {
            assertTrue(isUrlEnabled("invalid[}regex"))
        }
    }

    @Test
    fun testGetCapturePublicKey() {
        val otelCfg = OpenTelemetryConfiguration(
            SpanSinkImpl(),
            LogSinkImpl(),
            SystemInfo(),
            "my-id"
        )
        val json = ResourceReader.readResourceAsText("public_key_config.json")
        val localConfig = LocalConfigParser.buildConfig("aaa", false, json, EmbraceSerializer(), otelCfg, EmbLoggerImpl())
        val behavior = fakeNetworkBehavior(localCfg = localConfig::sdkConfig)
        assertEquals(testCleanPublicKey, behavior.getCapturePublicKey())
    }
}
