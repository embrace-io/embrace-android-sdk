@file:OptIn(io.embrace.android.embracesdk.semconv.ExperimentalSemconv::class)

package io.embrace.android.embracesdk.testcases.session

import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.getStartTime
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.assertions.isBackgroundOnlyPart
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fixtures.FAKE_USER_SESSION_ID
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeNativeCrashStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectionType
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.testcases.features.createNativeSymbolsForCurrentArch
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_DEAD_SESSION_PART_ID
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_EXPIRED_USER_SESSION_ID
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_SDK_START_TIME_MS
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.actions.createStoredNativeCrashData
import io.embrace.android.embracesdk.testframework.assertions.assertFinalPart
import io.embrace.android.embracesdk.testframework.assertions.assertNotFinalPart
import io.embrace.android.embracesdk.testframework.assertions.assertSessionIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [UPSIDE_DOWN_CAKE])
@RunWith(AndroidJUnit4::class)
internal class UserSessionResurrectionTest {

    private val serializer = TestPlatformSerializer()

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(fakeStorageLayer = true)
    }

    @Test
    fun `resurrected session part from JVM crash keeps old user session id when new user session is created offline`() {
        testRule.runTest(
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                persistExpiredUserSession(
                    sdkStartTimeMs = DEFAULT_SDK_START_TIME_MS,
                    userSessionId = DEFAULT_EXPIRED_USER_SESSION_ID,
                    cacheIncompletePartPayload = true,
                )
            },
            testCaseAction = {
                recordSession()
                simulateConnectionTypeChange(ConnectionType.WIFI)
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val resurrected = sessions.first()
                val live = sessions.last()
                assertEquals(DEFAULT_EXPIRED_USER_SESSION_ID, resurrected.getUserSessionId())
                assertNotEquals(DEFAULT_EXPIRED_USER_SESSION_ID, live.getUserSessionId())
                assertNotNull(live.getUserSessionId())
            },
        )
    }

    @Test
    fun `resurrected part of a terminated background-only session is stamped with the marker and final part`() {
        testRule.runTest(
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                persistExpiredUserSession(
                    sdkStartTimeMs = DEFAULT_SDK_START_TIME_MS,
                    userSessionId = FAKE_USER_SESSION_ID,
                    cacheIncompletePartPayload = true,
                    isBackgroundOnly = true,
                )
            },
            testCaseAction = {
                recordSession()
                simulateConnectionTypeChange(ConnectionType.WIFI)
            },
            assertAction = {
                val resurrected = getSessionEnvelopes(2).single { it.getUserSessionId() == FAKE_USER_SESSION_ID }

                assertTrue(resurrected.isBackgroundOnlyPart())
                resurrected.assertFinalPart(EmbSessionAttributes.EmbUserSessionTerminationReasonValues.MAX_DURATION_REACHED)
            },
        )
    }

    @Test
    fun `resurrected payloads from native crash keeps old user session id when new user session is created offline`() {
        testRule.runTest(
            instrumentedConfig = nativeCrashEnabledConfig(),
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                val expiredUserSessionStartTimeMs = persistExpiredUserSession(
                    sdkStartTimeMs = DEFAULT_SDK_START_TIME_MS,
                    userSessionId = DEFAULT_EXPIRED_USER_SESSION_ID,
                )
                setupNativeCrash(
                    sessionMetadata = fakeCachedSessionStoredTelemetryMetadata.copy(timestamp = expiredUserSessionStartTimeMs)
                )
            },
            testCaseAction = {
                recordSession()
                simulateConnectionTypeChange(ConnectionType.WIFI)
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val resurrected = sessions.first()
                val live = sessions.last()
                assertEquals(DEFAULT_EXPIRED_USER_SESSION_ID, resurrected.getUserSessionId())
                assertNotEquals(DEFAULT_EXPIRED_USER_SESSION_ID, live.getUserSessionId())

                getSingleLogEnvelope().getLastLog().assertSessionIds(DEFAULT_EXPIRED_USER_SESSION_ID, DEFAULT_DEAD_SESSION_PART_ID)
                assertNotEquals(live.getUserSessionId(), DEFAULT_EXPIRED_USER_SESSION_ID)
            },
        )
    }

    @Test
    fun `native crash has previous session ID when no session metadata is on disk even when resurrected in a new session`() {
        testRule.runTest(
            instrumentedConfig = nativeCrashEnabledConfig(),
            setupAction = {
                setupNativeCrash()
            },
            testCaseAction = {
                recordSession()
            },
            assertAction = {
                getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash)
                    .assertSessionIds(DEFAULT_EXPIRED_USER_SESSION_ID, DEFAULT_DEAD_SESSION_PART_ID)
                assertNotEquals(DEFAULT_EXPIRED_USER_SESSION_ID, getSingleSessionEnvelope().getUserSessionId())
            },
        )
    }

    @Test
    fun `native crash with empty session ids is sent`() {
        testRule.runTest(
            instrumentedConfig = nativeCrashEnabledConfig(),
            setupAction = {
                setupNativeCrash(userSessionId = "", sessionPartId = "")
            },
            testCaseAction = { recordSession() },
            assertAction = {
                getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash).assertSessionIds("", "")
            },
        )
    }

    @Test
    fun `native crash with no active session part carries the user session id and not a session part id`() {
        val userSessionId = "aabbccdd11223344aabbccdd11223344"
        testRule.runTest(
            instrumentedConfig = nativeCrashEnabledConfig(),
            setupAction = {
                setupNativeCrash(userSessionId = userSessionId, sessionPartId = "null")
            },
            testCaseAction = { recordSession() },
            assertAction = {
                getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash).assertSessionIds(userSessionId, "null")
            },
        )
    }

    @Test
    fun `restored user session continues a resurrected prior-process part without stamping it final`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        val priorProcessId = "prior-launch-process-id"
        testRule.runTest(
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                persistUserSession(
                    userSessionId = persistedId,
                    startMs = DEFAULT_SDK_START_TIME_MS - 1_000L,
                    lastActivityMs = DEFAULT_SDK_START_TIME_MS - 100L,
                )
                checkNotNull(fakeCacheStorageService).addPayload(
                    metadata = fakeCachedSessionStoredTelemetryMetadata.copy(
                        userSessionId = persistedId,
                        timestamp = DEFAULT_SDK_START_TIME_MS - 900L,
                    ),
                    data = fakeIncompleteSessionEnvelope(
                        sessionId = persistedId,
                        processIdentifier = priorProcessId,
                        startMs = DEFAULT_SDK_START_TIME_MS - 900L,
                        lastHeartbeatTimeMs = DEFAULT_SDK_START_TIME_MS - 800L,
                    ),
                )
            },
            testCaseAction = {
                recordSession()
                simulateConnectionTypeChange(ConnectionType.WIFI)
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val priorLaunchPart = sessions[0]
                val currentLaunchPart = sessions[1]

                // both parts belong to the one continued user session
                assertEquals(persistedId, priorLaunchPart.getUserSessionId())
                assertEquals(persistedId, currentLaunchPart.getUserSessionId())

                fun Envelope<SessionPartPayload>.processId() =
                    getSessionSpan()?.attributes?.findAttributeValue(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER)
                assertEquals(priorProcessId, priorLaunchPart.processId())
                assertNotEquals(priorProcessId, currentLaunchPart.processId())
                assertTrue(priorLaunchPart.getStartTime() < currentLaunchPart.getStartTime())

                priorLaunchPart.assertNotFinalPart()
                currentLaunchPart.assertNotFinalPart()
            },
        )
    }

    private fun nativeCrashEnabledConfig(): FakeInstrumentedConfig = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
        symbols = createNativeSymbolsForCurrentArch(mapOf("libfoo.so" to "symbol_content")),
    )

    private fun EmbraceSetupInterface.setupNativeCrash(
        userSessionId: String = DEFAULT_EXPIRED_USER_SESSION_ID,
        sessionPartId: String = DEFAULT_DEAD_SESSION_PART_ID,
        sessionMetadata: StoredTelemetryMetadata? = null,
    ) {
        val storedNativeCrashData = createStoredNativeCrashData(
            serializer = serializer,
            resourceFixtureName = "native_crash_1.txt",
            crashMetadata = fakeNativeCrashStoredTelemetryMetadata,
            sessionMetadata = sessionMetadata,
        )
        val nativeCrash = storedNativeCrashData.nativeCrash.copy(
            sessionPartId = sessionPartId,
            userSessionId = userSessionId,
        )

        val crashData = if (sessionMetadata == null) {
            storedNativeCrashData.copy(nativeCrash = nativeCrash)
        } else {
            storedNativeCrashData.copy(
                nativeCrash = nativeCrash,
                partEnvelope = fakeIncompleteSessionEnvelope(
                    sessionId = userSessionId,
                    startMs = sessionMetadata.timestamp,
                    lastHeartbeatTimeMs = sessionMetadata.timestamp + 1_000L,
                )
            )
        }
        setupCachedDataFromNativeCrash(crashData)
        setupFakeNativeCrash(
            serializer = serializer,
            crashData = crashData
        )
    }
}
