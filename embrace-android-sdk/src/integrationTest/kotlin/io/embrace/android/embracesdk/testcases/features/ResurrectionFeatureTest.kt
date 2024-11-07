package io.embrace.android.embracesdk.testcases.features

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.fakes.FakeNativeCrashService
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.config.remote.KillSwitchRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashId
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.getSessionSpan
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ResurrectionFeatureTest {

    private lateinit var cacheStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule()

    @Before
    fun setUp() {
        cacheStorageService = FakePayloadStorageService()
    }

    @Test
    fun `crashed session and native crash resurrected and sent properly`() {
        val sessionMetadata = fakeCachedSessionStoredTelemetryMetadata
        val lastHeartbeatTimeMs = sessionMetadata.timestamp + 1000L
        val deadSessionEnvelope = fakeIncompleteSessionEnvelope(
            startMs = sessionMetadata.timestamp,
            lastHeartbeatTimeMs = lastHeartbeatTimeMs
        )
        testRule.runTest(
            setupAction = {
                cacheStorageService.addPayload(sessionMetadata, deadSessionEnvelope)
                cacheStorageServiceProvider = { cacheStorageService }
                val nativeCrashService = fakeNativeFeatureModule.nativeCrashService as FakeNativeCrashService
                nativeCrashService.addNativeCrashData(
                    nativeCrashData = NativeCrashData(
                        nativeCrashId = "fake-native-crash-id",
                        sessionId = deadSessionEnvelope.getSessionId(),
                        timestamp = lastHeartbeatTimeMs,
                        appState = "foreground",
                        metadata = null,
                        unwindError = null,
                        crash = "somebinary",
                        symbols = null,
                        errors = null,
                        map = null
                    )
                )
            },
            testCaseAction = { },
            assertAction = {
                with(getSingleSessionEnvelope()) {
                    assertEquals(deadSessionEnvelope.getSessionId(), getSessionId())
                    with(checkNotNull(getSessionSpan())) {
                        assertEquals(lastHeartbeatTimeMs, endTimeNanos?.nanosToMillis())
                        assertEquals(Span.Status.ERROR, status)
                        assertEquals("fake-native-crash-id", attributes?.findAttributeValue(embCrashId.name))
                    }
                }
                assertNotNull(getSentNativeCrashes().singleOrNull())
            }
        )
    }

    @Test
    fun `resurrection attempt with v2 delivery layer off does not crash the SDK`() {
        testRule.runTest(
            remoteConfig = RemoteConfig(killSwitchConfig = KillSwitchRemoteConfig(v2StoragePct = 0f)),
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
}
