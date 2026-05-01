@file:OptIn(io.embrace.android.embracesdk.semconv.ExperimentalSemconv::class)
@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testcases.session

import android.app.ApplicationExitInfo
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.assertions.getUserSessionTerminationReason
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.TestAeiData
import io.embrace.android.embracesdk.fakes.TestPlatformSerializer
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.fakeIncompleteSessionEnvelope
import io.embrace.android.embracesdk.fakes.setupFakeAeiData
import io.embrace.android.embracesdk.fixtures.fakeCachedSessionStoredTelemetryMetadata
import io.embrace.android.embracesdk.fixtures.fakeNativeCrashStoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectionType
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.getSessionSpan
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbAeiAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_SESSION_PART_ID
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.INACTIVITY
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues.MANUAL
import io.embrace.android.embracesdk.testcases.features.createNativeSymbolsForCurrentArch
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_SDK_START_TIME_MS
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.embrace.android.embracesdk.testframework.actions.createStoredNativeCrashData
import io.opentelemetry.kotlin.semconv.SessionAttributes.SESSION_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@Config(sdk = [UPSIDE_DOWN_CAKE], shadows = [ShadowActivityManager::class])
@RunWith(AndroidJUnit4::class)
internal class UserSessionIntegrationTest {

    private val serializer = TestPlatformSerializer()
    private lateinit var cacheStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            fakeStorageLayer = true,
            workersToFake = listOf(Worker.Background.NonIoRegWorker, Worker.Background.LogMessageWorker),
        ).apply {
            getEmbLogger().throwOnInternalError = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.LogMessageWorker).blockingMode = false
        }.also {
            cacheStorageService = checkNotNull(it.fakeCacheStorageService)
        }
    }

    @Test
    fun `resurrected session part from JVM crash keeps old user session id when new user session is created offline`() {
        testRule.runTest(
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                persistExpiredUserSession(true)
            },
            testCaseAction = {
                recordSession()
                simulateConnectionTypeChange(ConnectionType.WIFI)
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val resurrected = sessions.first()
                val live = sessions.last()
                assertEquals(FAKE_USER_SESSION_ID, resurrected.getSessionId())
                assertNotEquals(FAKE_USER_SESSION_ID, live.getSessionId())
                assertNotEquals(FAKE_USER_SESSION_ID, live.getUserSessionId())
                assertNotNull(live.getUserSessionId())
            },
        )
    }

    @Test
    fun `resurrected payloads from native crash keeps old user session id when new user session is created offline`() {
        testRule.runTest(
            instrumentedConfig = nativeCrashEnabledConfig(),
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                persistExpiredUserSession()
                setupNativeCrash(
                    sessionMetadata = fakeCachedSessionStoredTelemetryMetadata.copy(timestamp = EXPIRED_SESSION_START_TIME_MS)
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
                assertEquals(FAKE_USER_SESSION_ID, resurrected.getSessionId())
                assertNotEquals(FAKE_USER_SESSION_ID, live.getSessionId())
                assertNotEquals(FAKE_USER_SESSION_ID, live.getUserSessionId())

                val crashAttrs = checkNotNull(getSingleLogEnvelope().getLastLog().attributes)
                assertEquals(FAKE_USER_SESSION_ID, crashAttrs.findAttributeValue(SESSION_ID))
                assertEquals(FAKE_SESSION_PART_ID, crashAttrs.findAttributeValue(EMB_SESSION_PART_ID))
                assertNotEquals(live.getUserSessionId(), crashAttrs.findAttributeValue(SESSION_ID))
            },
        )
    }

    @Test
    fun `session times out while device is offline works as expected`() {
        testRule.runTest(
            setupAction = {
                fakeNetworkConnectivityService.connectivityStatus = ConnectivityStatus.None
                persistExpiredUserSession(true)
            },
            testCaseAction = {
                recordSession()
                simulateConnectionTypeChange(ConnectionType.WIFI)
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val cached = sessions.first()
                val live = sessions.last()
                assertEquals(FAKE_USER_SESSION_ID, cached.getSessionId())
                assertNotEquals(FAKE_USER_SESSION_ID, live.getUserSessionId())
                assertNotEquals(cached.getSessionId(), live.getUserSessionId())
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
                val attrs = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash).attributes)
                assertEquals(FAKE_USER_SESSION_ID, attrs.findAttributeValue(SESSION_ID))
                assertEquals(FAKE_SESSION_PART_ID, attrs.findAttributeValue(EMB_SESSION_PART_ID))
                assertNotEquals(FAKE_USER_SESSION_ID, getSingleSessionEnvelope().getUserSessionId())
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
                val attrs = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash).attributes)
                assertEquals("", attrs.findAttributeValue(SESSION_ID))
                assertEquals("", attrs.findAttributeValue(EMB_SESSION_PART_ID))
            },
        )
    }

    @Test
    fun `AEI payload carries old user session id when persisted session expired between app instances`() {
        val processStateSummary = "${FAKE_SESSION_PART_ID}_${FAKE_USER_SESSION_ID}"
        val nativeAei = TestAeiData(
            reason = ApplicationExitInfo.REASON_CRASH_NATIVE,
            status = 6,
            description = "ndkCrash",
            trace = "someNdkCrashDetails",
            processStateSummary = processStateSummary,
        )
        testRule.runTest(
            instrumentedConfig = FakeInstrumentedConfig(
                enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
            ),
            setupAction = {
                persistExpiredUserSession()
                setupFakeAeiData(listOf(nativeAei.toAeiObject()))
            },
            testCaseAction = {
                flushLogBatch()
                recordSession()
            },
            assertAction = {
                val attrs = checkNotNull(getSingleLogEnvelope().getLastLog().attributes)
                assertEquals(FAKE_SESSION_PART_ID, attrs.findAttributeValue(EmbAeiAttributes.AEI_SESSION_PART_ID))
                assertEquals(FAKE_USER_SESSION_ID, attrs.findAttributeValue(EmbAeiAttributes.AEI_USER_SESSION_ID))
                assertNotEquals(FAKE_USER_SESSION_ID, getSingleSessionEnvelope().getUserSessionId())
            },
        )
    }

    @Test
    fun `endUserSession from background when background activity is off does not end user session`() {
        testRule.runTest(
            testCaseAction = {
                recordSession()
                embrace.endUserSession()
                recordSession()
            },
            assertAction = {
                val parts = getSessionEnvelopes(2)
                assertEquals(parts[0].getUserSessionId(), parts[1].getUserSessionId())
            },
        )
    }

    @Test
    fun `manual session end kill switch does not suppress timer-driven inactivity session transition`() {
        val inactivityTimeoutSeconds = 30
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                sessionConfig = SessionRemoteConfig(isEnabled = true),
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = inactivityTimeoutSeconds),
            ),
            testCaseAction = {
                recordSession()
                clock.tick(inactivityTimeoutSeconds * 1_000L + 1L)
                testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).runCurrentlyBlocked()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
            },
        )
    }

    @Test
    fun `inactivity timer at exact boundary expires the user session`() {
        val inactivityTimeoutSeconds = 30
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = inactivityTimeoutSeconds),
            ),
            testCaseAction = {
                recordSession()
                clock.tick(inactivityTimeoutSeconds * 1_000L)
                testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).runCurrentlyBlocked()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
            },
        )
    }

    @Test
    fun `max duration timer at exact boundary expires the user session`() {
        val maxDurationSeconds = 3600
        testRule.runTest(
            persistedRemoteConfig = matchedTimeoutConfig(maxDurationSeconds),
            testCaseAction = {
                recordSession()
                clock.tick(maxDurationSeconds * 1_000L)
                testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).runCurrentlyBlocked()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
            },
        )
    }

    @Test
    fun `manual end after max duration but before the job is processed terminates with MANUAL`() {
        val maxDurationSeconds = 600
        testRule.runTest(
            persistedRemoteConfig = matchedTimeoutConfig(maxDurationSeconds),
            testCaseAction = {
                recordSession {
                    clock.tick(maxDurationSeconds * 1_000L)
                    embrace.endUserSession()
                }
                testRule.setup.getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).runCurrentlyBlocked()
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
                assertEquals(MANUAL, sessions[0].getUserSessionTerminationReason())
            },
        )
    }

    @Test
    fun `foregrounding before inactivity timeout continues the same user session`() {
        runPartsBoundaryTest(
            inactivityTimeoutSeconds = 30,
            partsGapSeconds = 29
        ) { sessions ->
            assertEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
        }
    }

    @Test
    fun `foregrounding at inactivity boundary creates a new user session`() {
        runPartsBoundaryTest(
            inactivityTimeoutSeconds = 60,
            partsGapSeconds = 60
        ) { sessions ->
            assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
        }
    }

    @Test
    fun `foregrounding after inactivity boundary creates a new user session`() {
        runPartsBoundaryTest(
            inactivityTimeoutSeconds = 30,
            partsGapSeconds = 31
        ) { sessions ->
            assertNotEquals(sessions[0].getUserSessionId(), sessions[1].getUserSessionId())
        }
    }

    @Test
    fun `log call invoked before session reaches its max duration but processed after is attributed to the new session`() {
        val maxDurationSeconds = 300
        val maxDurationMs = maxDurationSeconds * 1_000L
        testRule.runTest(
            persistedRemoteConfig = matchedTimeoutConfig(maxDurationSeconds),
            testCaseAction = {
                recordSession()
                val latch = CountDownLatch(1)
                val job = Thread {
                    embrace.logInfo("late-log")
                    latch.countDown()
                }
                clock.tick(maxDurationMs + 1L)
                recordSession {
                    job.start()
                    latch.await(1, TimeUnit.SECONDS)
                    flushLogBatch()
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val oldUserSessionId = sessions[0].getUserSessionId()
                val newUserSessionId = sessions[1].getUserSessionId()
                assertNotEquals(oldUserSessionId, newUserSessionId)

                val logSessionId = getSingleLogEnvelope().getLastLog().attributes?.findAttributeValue(SESSION_ID)
                assertEquals(newUserSessionId, logSessionId)
            },
        )
    }

    @Test
    fun `log call invoked after session reaches its max duration but processed before new session is attributed to the old session`() {
        val maxDurationSeconds = 300
        val maxDurationMs = maxDurationSeconds * 1_000L
        testRule.runTest(
            persistedRemoteConfig = matchedTimeoutConfig(maxDurationSeconds),
            testCaseAction = {
                val latch = CountDownLatch(1)
                val job = Thread {
                    embrace.logInfo("late-log")
                    latch.countDown()
                }
                recordSession {
                    clock.tick(maxDurationMs + 1L)
                    job.start()
                    latch.await(1, TimeUnit.SECONDS)
                    flushLogBatch()
                }
                clock.tick(maxDurationMs + 1L)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val sessionSpanFromOldPart = checkNotNull(sessions[0].getSessionSpan())
                val oldUserSessionId = sessions[0].getUserSessionId()
                val newUserSessionId = sessions[1].getUserSessionId()
                assertNotEquals(oldUserSessionId, newUserSessionId)

                val log = getSingleLogEnvelope().getLastLog()
                val logSessionId = log.attributes?.findAttributeValue(SESSION_ID)
                assertEquals(oldUserSessionId, logSessionId)
                assertTrue(checkNotNull(log.timeUnixNano) <= checkNotNull(sessionSpanFromOldPart.endTimeNanos))
            },
        )
    }

    @Test
    fun `log queued before endUserSession but processed after is attributed to the post-end session`() {
        testRule.runTest(
            testCaseAction = {
                val latch = CountDownLatch(1)
                val job = Thread {
                    embrace.logInfo("late-log")
                    latch.countDown()
                }
                recordSession {
                    clock.tick(10_000L)
                    embrace.endUserSession()
                    job.start()
                    latch.await(1, TimeUnit.SECONDS)
                    flushLogBatch()
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val oldUserSessionId = sessions[0].getUserSessionId()
                val newUserSessionId = sessions[1].getUserSessionId()
                assertNotEquals(oldUserSessionId, newUserSessionId)

                val logSessionId = getSingleLogEnvelope().getLastLog().attributes?.findAttributeValue(SESSION_ID)
                assertEquals(newUserSessionId, logSessionId)
            },
        )
    }

    private fun matchedTimeoutConfig(seconds: Int): RemoteConfig = RemoteConfig(
        userSession = UserSessionRemoteConfig(
            maxDurationSeconds = seconds,
            inactivityTimeoutSeconds = seconds,
        ),
    )

    private fun nativeCrashEnabledConfig(): FakeInstrumentedConfig = FakeInstrumentedConfig(
        enabledFeatures = FakeEnabledFeatureConfig(nativeCrashCapture = true),
        symbols = createNativeSymbolsForCurrentArch(mapOf("libfoo.so" to "symbol_content")),
    )

    private fun EmbraceSetupInterface.persistExpiredUserSession(
        cacheIncompletePartPayload: Boolean = false
    ) {
        val sessionStartMs = EXPIRED_SESSION_START_TIME_MS
        persistUserSession(
            sessionId = FAKE_USER_SESSION_ID,
            startMs = sessionStartMs,
            lastActivityMs = sessionStartMs + 59_999L
        )
        if (cacheIncompletePartPayload) {
            val metadata = fakeCachedSessionStoredTelemetryMetadata.copy(timestamp = sessionStartMs)
            cacheStorageService.addPayload(
                metadata =  metadata,
                data = fakeIncompleteSessionEnvelope(
                    sessionId = FAKE_USER_SESSION_ID,
                    startMs = metadata.timestamp,
                    lastHeartbeatTimeMs = metadata.timestamp + 1_000L,
                    processIdentifier = metadata.processIdentifier
                )
            )
        }
    }

    private fun EmbraceSetupInterface.setupNativeCrash(
        userSessionId: String = FAKE_USER_SESSION_ID,
        sessionPartId: String = FAKE_SESSION_PART_ID,
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

    private fun EmbraceActionInterface.flushLogBatch() {
        clock.tick(2000L)
        testRule.setup.getFakedWorkerExecutor(Worker.Background.LogMessageWorker).runCurrentlyBlocked()
    }

    private fun runPartsBoundaryTest(
        inactivityTimeoutSeconds: Int,
        partsGapSeconds: Int = 0,
        assertions: (sessions: List<Envelope<SessionPartPayload>>) -> Unit,
    ) {
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(inactivityTimeoutSeconds = inactivityTimeoutSeconds),
            ),
            testCaseAction = {
                recordSession()
                clock.tick(partsGapSeconds.seconds.inWholeMilliseconds)
                recordSession()
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                assertions(sessions)
                val reason = sessions[0].getUserSessionTerminationReason()
                if (reason != null && partsGapSeconds >= inactivityTimeoutSeconds) {
                    assertEquals(INACTIVITY, reason)
                }
            },
        )
    }

    private companion object {
        const val FAKE_USER_SESSION_ID = "aabbccdd11223344aabbccdd11223344"
        const val FAKE_SESSION_PART_ID = "11112222333344445555666677778888"
        const val EXPIRED_SESSION_START_TIME_MS = DEFAULT_SDK_START_TIME_MS - 1_800_000L - 60_000L
    }
}
