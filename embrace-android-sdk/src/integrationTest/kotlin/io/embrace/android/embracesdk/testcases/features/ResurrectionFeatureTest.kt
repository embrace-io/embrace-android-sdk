package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.ResourceReader
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashId
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.getLastLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ResurrectionFeatureTest {

    private val serializer = TestPlatformSerializer()
    private lateinit var cacheStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Before
    fun setUp() {
        cacheStorageService =
            FakePayloadStorageService(
                processIdProvider = {
                    testRule.setup.overriddenOpenTelemetryModule.openTelemetryConfiguration.processIdentifier
                }
            )
    }

    @Test
    fun `crashed session and native crash resurrected and sent properly`() {
        val sessionMetadata = fakeCachedSessionStoredTelemetryMetadata
        val lastHeartbeatTimeMs = sessionMetadata.timestamp + 1000L

        val nativeCrashData: NativeCrashData = serializer.fromJson(
            ResourceReader.readResource("native_crash_1.txt"),
            NativeCrashData::class.java
        )
        val deadSessionEnvelope = fakeIncompleteSessionEnvelope(
            startMs = sessionMetadata.timestamp,
            lastHeartbeatTimeMs = lastHeartbeatTimeMs,
            sessionId = nativeCrashData.sessionId
        )
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true)),
            setupAction = {
                setupFakeDeadSession(cacheStorageService, sessionMetadata, deadSessionEnvelope)
                setupFakeNativeCrash(serializer, nativeCrashData)
            },
            testCaseAction = {},
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertDeadSessionResurrected(deadSessionEnvelope, lastHeartbeatTimeMs, nativeCrashData, embCrashId)
                }
                val envelope = getSingleLogEnvelope()
                val log = envelope.getLastLog()
                assertNativeCrashSent(log, nativeCrashData, deadSessionEnvelope, testRule.setup.symbols)
            }
        )
    }

    @Test
    fun `empty crash envelope not available for native crash resurrection if background activity is enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(100f)
            ),
            setupAction = {
                cacheStorageServiceProvider = { cacheStorageService }
            },
            testCaseAction = {
                recordSession()
                recordSession()
            },
            assertAction = {
                getSessionEnvelopes(2)
                assertTrue(cacheStorageService.getCachedCrashEnvelope().isEmpty())
            }
        )
    }

    @Test
    fun `one empty crash envelope available for native crash resurrection if background activity is not enabled`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                backgroundActivityConfig = BackgroundActivityRemoteConfig(0f)
            ),
            setupAction = {
                cacheStorageServiceProvider = { cacheStorageService }
            },
            testCaseAction = {
                recordSession()
                recordSession()
                recordSession()
            },
            assertAction = {
                getSessionEnvelopes(3)
                with(cacheStorageService.getCachedCrashEnvelope().single()) {
                    assertEquals(SupportedEnvelopeType.CRASH, envelopeType)
                    assertEquals(PayloadType.NATIVE_CRASH, payloadType)
                    assertFalse(complete)
                }

            }
        )
    }

    @Test
    fun `resurrection attempt with v2 delivery layer off does not crash the SDK`() {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(killSwitchConfig = KillSwitchRemoteConfig(v2StoragePct = 0f)),
            setupAction = {
                useMockWebServer = false
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                assertNotNull(getSessionEnvelopesV1(1).single())
            }
        )
    }

    private fun FakePayloadStorageService.getCachedCrashEnvelope(): List<StoredTelemetryMetadata> {
        return storedPayloadMetadata().filter { !it.complete && it.envelopeType == SupportedEnvelopeType.CRASH }
    }
}
