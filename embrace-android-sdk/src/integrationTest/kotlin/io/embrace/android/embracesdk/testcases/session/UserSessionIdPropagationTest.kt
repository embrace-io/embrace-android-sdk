package io.embrace.android.embracesdk.testcases.session

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.assertions.SessionIds
import io.embrace.android.embracesdk.assertions.assertSessionIds
import io.embrace.android.embracesdk.assertions.findSessionPartSpan
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.getLogsOfType
import io.embrace.android.embracesdk.assertions.getUserSessionId
import io.embrace.android.embracesdk.assertions.toMap
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.TestAeiData
import io.embrace.android.embracesdk.fakes.config.FakeEnabledFeatureConfig
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.setupFakeAeiData
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.UserSessionRemoteConfig
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.getSessionPartSpan
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbAeiAttributes.AEI_SESSION_PART_ID
import io.embrace.android.embracesdk.semconv.EmbAeiAttributes.AEI_USER_SESSION_ID
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_SESSION_PART_ID
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_ID
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_DEAD_SESSION_PART_ID
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_EXPIRED_USER_SESSION_ID
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule.Companion.DEFAULT_SDK_START_TIME_MS
import io.embrace.android.embracesdk.testframework.actions.EmbraceActionInterface
import io.embrace.android.embracesdk.testframework.actions.EmbraceSetupInterface
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.kotlin.semconv.SessionAttributes.SESSION_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Asserts that user session ID and session part IDs propagate to logs and spans.
 *
 * Test cases should assert both on the HTTP request sent to Embrace's servers and the OTLP request.
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionIdPropagationTest {

    private lateinit var payloadStorageService: FakePayloadStorageService

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule {
        EmbraceSetupInterface(
            fakeStorageLayer = true,
            workersToFake = listOf(Worker.Background.LogMessageWorker, Worker.Background.NonIoRegWorker),
        ).apply {
            getFakedWorkerExecutor(Worker.Background.LogMessageWorker).blockingMode = false
            getFakedWorkerExecutor(Worker.Background.NonIoRegWorker).blockingMode = false
        }.also {
            payloadStorageService = checkNotNull(it.fakePayloadStorageService)
        }
    }

    @Test
    fun `session span carries the user session id and session part id`() {
        testRule.runTest(
            testCaseAction = { recordSession() },
            assertAction = {
                getSingleSessionEnvelope().assertSessionIds()
            },
            otelExportAssertion = {
                val span = awaitSpansWithType(1, EmbType.Ux.Session).single()
                span.attributes.toStringMap().assertSessionIds()
            },
        )
    }

    @Test
    fun `session envelope carries the user session id and session part id`() {
        testRule.runTest(
            testCaseAction = { recordSession() },
            assertAction = {
                getSingleSessionEnvelope().assertSessionIds()
            },
            otelExportAssertion = {
                val span = awaitSpansWithType(1, EmbType.Ux.Session).single()
                span.attributes.toStringMap().assertSessionIds()
            },
        )
    }

    @Test
    fun `log emitted within active user session carries the user session id and session part id`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logMessage("test", Severity.INFO)
                    flushLogBatch()
                }
            },
            assertAction = {
                val sessionIds = getSingleSessionEnvelope().assertSessionIds()
                val logIds = getSingleLogEnvelope().getLogOfType(EmbType.System.Log).assertSessionIds()
                assertEquals(sessionIds.userSessionId, logIds.userSessionId)
                assertEquals(sessionIds.partId, logIds.partId)
            },
            otelExportAssertion = {
                val span = awaitSpansWithType(1, EmbType.Ux.Session).single()
                val sessionIds = span.attributes.toStringMap().assertSessionIds()
                val log = awaitLogs(1) { it.attributes.toStringMap().containsKey(EmbType.System.Log.key) }.single()
                val logIds = log.attributes.toStringMap().assertSessionIds()
                assertEquals(sessionIds.userSessionId, logIds.userSessionId)
                assertEquals(sessionIds.partId, logIds.partId)
            },
        )
    }

    @Test
    fun `log before foreground carries the restored user session id when a persisted session is continued`() {
        val persistedId = "aabbccdd11223344aabbccdd11223344"
        testRule.runTest(
            setupAction = {
                persistUserSession(
                    userSessionId = persistedId,
                    startMs = DEFAULT_SDK_START_TIME_MS - 1_000L,
                    lastActivityMs = DEFAULT_SDK_START_TIME_MS - 100L,
                )
            },
            testCaseAction = {
                embrace.logMessage("cold-window-log", Severity.INFO)
                recordSession {
                    flushLogBatch()
                }
            },
            assertAction = {
                val sessionUserSessionId = getSingleSessionEnvelope().getUserSessionId()
                val logAttrs = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.Log).attributes)
                val logUserSessionId = logAttrs.findAttributeValue(EMB_USER_SESSION_ID)
                assertEquals(persistedId, sessionUserSessionId)
                assertEquals(persistedId, logUserSessionId)
                assertTrue(logAttrs.findAttributeValue(EMB_SESSION_PART_ID).isNullOrEmpty())
            },
        )
    }

    @Test
    fun `log before foreground carries a new user session id when the persisted session is dropped`() {
        testRule.runTest(
            setupAction = {
                persistExpiredUserSession(
                    sdkStartTimeMs = DEFAULT_SDK_START_TIME_MS,
                    userSessionId = DEFAULT_EXPIRED_USER_SESSION_ID,
                )
            },
            testCaseAction = {
                embrace.logMessage("cold-window-log", Severity.INFO)
                recordSession {
                    flushLogBatch()
                }
            },
            assertAction = {
                val sessionUserSessionId = getSingleSessionEnvelope().getUserSessionId()
                val logAttrs = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.Log).attributes)
                val logUserSessionId = logAttrs.findAttributeValue(EMB_USER_SESSION_ID)
                assertTrue(sessionUserSessionId.isNotEmpty())
                assertNotEquals(DEFAULT_EXPIRED_USER_SESSION_ID, sessionUserSessionId)
                assertEquals(sessionUserSessionId, logUserSessionId)
                assertTrue(logAttrs.findAttributeValue(EMB_SESSION_PART_ID).isNullOrEmpty())
            },
        )
    }

    @Test
    fun `log before foreground carries the new user session id when no session is persisted`() {
        testRule.runTest(
            testCaseAction = {
                embrace.logMessage("cold-window-log", Severity.INFO)
                recordSession {
                    flushLogBatch()
                }
            },
            assertAction = {
                val sessionUserSessionId = getSingleSessionEnvelope().getUserSessionId()
                val logAttrs = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.Log).attributes)
                val logUserSessionId = logAttrs.findAttributeValue(EMB_USER_SESSION_ID)
                assertTrue(sessionUserSessionId.isNotEmpty())
                assertEquals(sessionUserSessionId, logUserSessionId)
                assertTrue(logAttrs.findAttributeValue(EMB_SESSION_PART_ID).isNullOrEmpty())
            },
        )
    }

    @Test
    fun `log in second part of same user session carries same user session id but different session part id`() {
        testRule.runTest(
            testCaseAction = {
                recordSession { embrace.logMessage("part1", Severity.INFO) }
                recordSession { embrace.logMessage("part2", Severity.INFO) }
            },
            assertAction = {
                val logs = getSingleLogEnvelope().getLogsOfType(EmbType.System.Log)
                check(logs.size == 2)
                val ids1 = logs[0].assertSessionIds()
                val ids2 = logs[1].assertSessionIds()
                assertEquals(ids1.userSessionId, ids2.userSessionId)
                assertNotEquals(ids1.partId, ids2.partId)
            },
            otelExportAssertion = {
                val logs = awaitLogs(2) { it.attributes.toStringMap().containsKey(EmbType.System.Log.key) }
                val ids1 = logs[0].attributes.toStringMap().assertSessionIds()
                val ids2 = logs[1].attributes.toStringMap().assertSessionIds()
                assertEquals(ids1.userSessionId, ids2.userSessionId)
                assertNotEquals(ids1.partId, ids2.partId)
            },
        )
    }

    @Test
    fun `logs across different user sessions carry different user session ids`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.logMessage("first-session-log", Severity.INFO)
                    clock.tick(10_000)
                    embrace.endUserSession()
                    clock.tick(1_000)
                    embrace.logMessage("second-session-log", Severity.INFO)
                }
            },
            assertAction = {
                val logs = getSingleLogEnvelope().getLogsOfType(EmbType.System.Log)
                check(logs.size == 2)
                val ids1 = logs[0].assertSessionIds()
                val ids2 = logs[1].assertSessionIds()
                assertNotEquals(ids1.userSessionId, ids2.userSessionId)
            },
            otelExportAssertion = {
                val logs = awaitLogs(2) { it.attributes.toStringMap().containsKey(EmbType.System.Log.key) }
                val ids1 = logs[0].attributes.toStringMap().assertSessionIds()
                val ids2 = logs[1].attributes.toStringMap().assertSessionIds()
                assertNotEquals(ids1.userSessionId, ids2.userSessionId)
            },
        )
    }

    @Test
    fun `JVM exception log contains session part id and user session id`() {
        testRule.runTest(
            testCaseAction = {
                recordSession { simulateJvmUncaughtException(RuntimeException("test crash")) }
            },
            assertAction = {
                // crash teardown shuts down delivery, so read the persisted envelope directly
                payloadStorageService.getPersistedCrashLog().getLastLog().assertSessionIds()
            },
            otelExportAssertion = {
                val log = awaitLogs(1) { it.attributes.toStringMap().containsKey(EmbType.System.Crash.key) }.single()
                log.attributes.toStringMap().assertSessionIds()
            },
        )
    }

    @Test
    fun `native crash contains session part id and user session id`() {
        val sessionPartId = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d"
        val userSessionId = "2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e"
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    val nativeCrash = NativeCrashData(
                        nativeCrashId = "test-crash-report-id",
                        sessionPartId = sessionPartId,
                        userSessionId = userSessionId,
                        timestamp = clock.now(),
                        crash = null,
                        symbols = null,
                    )
                    findDataSource<NativeCrashDataSource>().sendNativeCrash(
                        nativeCrash = nativeCrash,
                        userSessionProperties = emptyMap(),
                        metadata = emptyMap(),
                    )
                }
            },
            assertAction = {
                val logAttrs = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.NativeCrash).attributes)
                assertEquals(sessionPartId, logAttrs.findAttributeValue(EMB_SESSION_PART_ID))
                assertEquals(userSessionId, logAttrs.findAttributeValue(EMB_USER_SESSION_ID))
                assertEquals(userSessionId, logAttrs.findAttributeValue(SESSION_ID))
            },
            otelExportAssertion = {
                val log = awaitLogs(1) { it.attributes.toStringMap().containsKey(EmbType.System.NativeCrash.key) }.single()
                val logAttrs = log.attributes.toStringMap()
                assertEquals(sessionPartId, logAttrs[EMB_SESSION_PART_ID])
                assertEquals(userSessionId, logAttrs[EMB_USER_SESSION_ID])
                assertEquals(userSessionId, logAttrs[SESSION_ID])
            },
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R], shadows = [ShadowActivityManager::class])
    fun `AEI log contains session part id and user session id`() {
        val sessionPartId = "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d"
        val userSessionId = "2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e"
        var currentUserSessionId: String? = null
        testRule.runTest(
            setupAction = {
                val ctx = ApplicationProvider.getApplicationContext<Application>()
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                Shadows.shadowOf(am).addApplicationExitInfo(
                    mockk<ApplicationExitInfo>(relaxed = true) {
                        every { timestamp } returns 15000000000L
                        every { pid } returns 6952
                        every { processStateSummary } returns "${sessionPartId}_${userSessionId}".toByteArray()
                        every { importance } returns 100
                        every { pss } returns 1509123409L
                        every { rss } returns 1123409L
                        every { reason } returns ApplicationExitInfo.REASON_ANR
                        every { status } returns 0
                        every { description } returns "test-anr"
                        every { traceInputStream } returns "test-trace".byteInputStream()
                    }
                )
            },
            testCaseAction = { recordSession() },
            assertAction = {
                // the AEI log was emitted before the app foregrounded, so it carries the id of
                // the flavour-pending user session - which resolved to the recorded session's
                // user session when the app entered the foreground - and no session part id
                currentUserSessionId = getSingleSessionEnvelope().getUserSessionId()
                val logAttrs = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.Exit).attributes)
                assertEquals(sessionPartId, logAttrs.findAttributeValue(AEI_SESSION_PART_ID))
                assertEquals(userSessionId, logAttrs.findAttributeValue(AEI_USER_SESSION_ID))
                assertEquals("", logAttrs.findAttributeValue(EMB_SESSION_PART_ID))
                assertEquals(currentUserSessionId, logAttrs.findAttributeValue(EMB_USER_SESSION_ID))
                assertEquals(currentUserSessionId, logAttrs.findAttributeValue(SESSION_ID))
            },
            otelExportAssertion = {
                val log = awaitLogs(1) { it.attributes.toStringMap().containsKey(EmbType.System.Exit.key) }.single()
                val logAttrs = log.attributes.toStringMap()
                assertEquals(sessionPartId, logAttrs[AEI_SESSION_PART_ID])
                assertEquals(userSessionId, logAttrs[AEI_USER_SESSION_ID])
                assertEquals("", logAttrs[EMB_SESSION_PART_ID])
                assertEquals(currentUserSessionId, logAttrs[EMB_USER_SESSION_ID])
                assertEquals(currentUserSessionId, logAttrs[SESSION_ID])
            },
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R], shadows = [ShadowActivityManager::class])
    fun `AEI payload carries old user session id when persisted session expired between app instances`() {
        val processStateSummary = "${DEFAULT_DEAD_SESSION_PART_ID}_${DEFAULT_EXPIRED_USER_SESSION_ID}"
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
                persistExpiredUserSession(
                    sdkStartTimeMs = DEFAULT_SDK_START_TIME_MS,
                    userSessionId = DEFAULT_EXPIRED_USER_SESSION_ID,
                )
                setupFakeAeiData(listOf(nativeAei.toAeiObject()))
            },
            testCaseAction = {
                flushLogBatch()
                recordSession()
            },
            assertAction = {
                val attrs = checkNotNull(getSingleLogEnvelope().getLastLog().attributes)
                assertEquals(DEFAULT_DEAD_SESSION_PART_ID, attrs.findAttributeValue(AEI_SESSION_PART_ID))
                assertEquals(DEFAULT_EXPIRED_USER_SESSION_ID, attrs.findAttributeValue(AEI_USER_SESSION_ID))
                assertNotEquals(DEFAULT_EXPIRED_USER_SESSION_ID, getSingleSessionEnvelope().getUserSessionId())
            },
        )
    }

    @Test
    fun `log call invoked before user session reaches max duration but processed after is attributed to the new user session`() {
        val maxDurationSeconds = 300
        val maxDurationMs = maxDurationSeconds * 1_000L
        testRule.runTest(
            // Set inactivity timeout to max duration so the timer doesn't prematurely end teh session
            persistedRemoteConfig = RemoteConfig(
                userSession = UserSessionRemoteConfig(
                    maxDurationSeconds = maxDurationSeconds,
                    inactivityTimeoutSeconds = maxDurationSeconds
                ),
            ),
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
    fun `log call invoked after user session reaches max duration but processed before is attributed to the old user session`() {
        val maxDurationSeconds = 300
        val maxDurationMs = maxDurationSeconds * 1_000L
        testRule.runTest(
            persistedRemoteConfig = RemoteConfig(
                // Set inactivity timeout to max duration so the timer doesn't prematurely end teh session
                userSession = UserSessionRemoteConfig(
                    maxDurationSeconds = maxDurationSeconds,
                    inactivityTimeoutSeconds = maxDurationSeconds
                ),
            ),
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
                val sessionPartSpanFromOldPart = checkNotNull(sessions[0].getSessionPartSpan())
                val oldUserSessionId = sessions[0].getUserSessionId()
                val newUserSessionId = sessions[1].getUserSessionId()
                assertNotEquals(oldUserSessionId, newUserSessionId)

                val log = getSingleLogEnvelope().getLastLog()
                val logSessionId = log.attributes?.findAttributeValue(SESSION_ID)
                assertEquals(oldUserSessionId, logSessionId)
                assertTrue(checkNotNull(log.timeUnixNano) <= checkNotNull(sessionPartSpanFromOldPart.endTimeNanos))
            },
        )
    }

    @Test
    fun `log queued before endUserSession but processed after is attributed to the post-end user session`() {
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

    private fun Envelope<SessionPartPayload>.assertSessionIds(): SessionIds =
        checkNotNull(findSessionPartSpan().attributes).toMap().assertSessionIds()

    private fun Log.assertSessionIds(): SessionIds =
        checkNotNull(attributes).toMap().assertSessionIds()

    private fun EmbraceActionInterface.flushLogBatch() {
        clock.tick(2000L)
        testRule.setup.getFakedWorkerExecutor(Worker.Background.LogMessageWorker).runCurrentlyBlocked()
    }
}
