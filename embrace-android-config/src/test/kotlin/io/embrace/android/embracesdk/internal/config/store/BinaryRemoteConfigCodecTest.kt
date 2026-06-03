package io.embrace.android.embracesdk.internal.config.store

import io.embrace.android.embracesdk.internal.config.remote.AppExitInfoConfig
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.DataRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.LogRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkCaptureRuleRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.OtelKotlinSdkConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.ThreadBlockageRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UiRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal class BinaryRemoteConfigCodecTest {

    private val encoder = BinaryRemoteConfigEncoder()
    private val decoder = BinaryRemoteConfigDecoder()

    private fun encode(config: RemoteConfig, deviceId: String = TEST_DEVICE_ID): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out -> with(encoder) { out.write(deviceId, config) } }
        return baos.toByteArray()
    }

    private fun decode(
        bytes: ByteArray,
        onHeader: (BinaryRemoteConfigDecoder.Header) -> Boolean = { false },
    ): RemoteConfig? =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            with(decoder) { input.readRemoteConfig(onHeader) }
        }

    @Test
    fun `empty config round-trips`() {
        val config = RemoteConfig()
        assertEquals(config, decode(encode(config)))
    }

    @Test
    fun `decoding rejects an unsupported version`() {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { it.writeInt(BinaryRemoteConfigFormat.VERSION + 1) }
        assertNull("decoding should reject unsupported version", decode(baos.toByteArray()))
    }

    @Test
    fun `fully populated config round-trips`() {
        val config = RemoteConfig(
            threshold = 50,
            disabledEventAndLogPatterns = setOf("event-a", "event-b"),
            disabledUrlPatterns = setOf("https://example.com"),
            networkCaptureRules = setOf(
                NetworkCaptureRuleRemoteConfig(
                    id = "rule-1",
                    duration = 1000L,
                    method = "GET",
                    urlRegex = ".*",
                    expiresIn = 5L,
                    maxSize = 200L,
                    maxCount = 3,
                    statusCodes = setOf(200, -1),
                )
            ),
            uiConfig = UiRemoteConfig(breadcrumbs = 1, taps = 2, webViews = 3, fragments = 4),
            networkConfig = NetworkRemoteConfig(defaultCaptureLimit = 10, domainLimits = mapOf("x" to 1, "y" to 2)),
            sessionConfig = SessionRemoteConfig(isEnabled = true),
            logConfig = LogRemoteConfig(100, 1, 2, 3),
            threadBlockageRemoteConfig = ThreadBlockageRemoteConfig(1, 2L, 3, 4, 5, 6, 7),
            dataConfig = DataRemoteConfig(pctThermalStatusEnabled = 0.5f),
            killSwitchConfig = KillSwitchRemoteConfig(sigHandlerDetection = true, jetpackCompose = false),
            internalExceptionCaptureEnabled = true,
            appExitInfoConfig = AppExitInfoConfig(appExitInfoTracesLimit = 100, pctAeiCaptureEnabled = 0.25f, aeiMaxNum = 5),
            backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 0.75f),
            maxUserSessionProperties = 42,
            networkSpanForwardingRemoteConfig = NetworkSpanForwardingRemoteConfig(pctEnabled = 0.1f),
            uiLoadInstrumentationEnabled = false,
            otelKotlinSdkConfig = OtelKotlinSdkConfig(pctEnabled = 0.9f),
            pctStateCaptureEnabledV2 = 0.2f,
            pctNetworkCallbackConnectivityServiceEnabled = 0.3f,
            pctNavigationStateCaptureEnabled = 0.4f,
            userSession = UserSessionRemoteConfig(maxDurationSeconds = 3600, inactivityTimeoutSeconds = 30),
        )

        val decoded = decode(encode(config))
        requireNotNull(decoded) { "BinaryRemoteConfigDecoder.decode returned null - is the version number correct?" }

        // AppExitInfoConfig is a plain class with no structural equals; compare the rest via copy
        // and its fields individually.
        assertEquals(config.copy(appExitInfoConfig = null), decoded.copy(appExitInfoConfig = null))
        assertEquals(config.appExitInfoConfig?.appExitInfoTracesLimit, decoded.appExitInfoConfig?.appExitInfoTracesLimit)
        assertEquals(config.appExitInfoConfig?.pctAeiCaptureEnabled, decoded.appExitInfoConfig?.pctAeiCaptureEnabled)
        assertEquals(config.appExitInfoConfig?.aeiMaxNum, decoded.appExitInfoConfig?.aeiMaxNum)

        // re-encoding the decoded config must produce identical bytes (stable round-trip)
        assertArrayEquals(encode(config), encode(decoded))
    }

    @Test
    fun `header exposes version, threshold and deviceId`() {
        var captured: BinaryRemoteConfigDecoder.Header? = null
        decode(encode(RemoteConfig(threshold = 42), deviceId = "device-abc")) {
            captured = it
            false
        }
        assertEquals(BinaryRemoteConfigFormat.VERSION, captured?.version)
        assertEquals(42, captured?.threshold)
        assertEquals("device-abc", captured?.deviceId)
    }

    @Test
    fun `gate stop short-circuits and returns a minimal config carrying only the threshold`() {
        val config = RemoteConfig(
            threshold = 10,
            maxUserSessionProperties = 99,
            disabledUrlPatterns = setOf("https://example.com"),
        )
        val decoded = decode(encode(config)) { true }
        assertEquals(RemoteConfig(threshold = 10), decoded)
    }

    @Test
    fun `gate continue decodes the full config`() {
        val config = RemoteConfig(threshold = 10, maxUserSessionProperties = 99)
        assertEquals(config, decode(encode(config)) { false })
    }

    private companion object {
        const val TEST_DEVICE_ID = "device-1234567890"
    }
}
