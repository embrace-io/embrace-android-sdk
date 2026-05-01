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
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getLastLog
import io.embrace.android.embracesdk.assertions.getLogOfType
import io.embrace.android.embracesdk.assertions.getLogsOfType
import io.embrace.android.embracesdk.assertions.toMap
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.toStringMap
import io.embrace.android.embracesdk.semconv.EmbAeiAttributes.AEI_SESSION_PART_ID
import io.embrace.android.embracesdk.semconv.EmbAeiAttributes.AEI_USER_SESSION_ID
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_SESSION_PART_ID
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EMB_USER_SESSION_ID
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.kotlin.semconv.SessionAttributes.SESSION_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager

/**
 * Asserts that user session ID and session part IDs propagate to logs and spans.
 *
 * Test cases should assert both on the HTTP request sent to Embrace's servers and the OTLP request.
 */
@RunWith(AndroidJUnit4::class)
internal class UserSessionIdPropagationTest {

    @Rule
    @JvmField
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

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
                recordSession { embrace.logMessage("test", Severity.INFO) }
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
                getSingleLogEnvelope().getLastLog().assertSessionIds()
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
                val logAttrs = checkNotNull(getSingleLogEnvelope().getLogOfType(EmbType.System.Exit).attributes)
                assertEquals(sessionPartId, logAttrs.findAttributeValue(AEI_SESSION_PART_ID))
                assertEquals(userSessionId, logAttrs.findAttributeValue(AEI_USER_SESSION_ID))
            },
            otelExportAssertion = {
                val log = awaitLogs(1) { it.attributes.toStringMap().containsKey(EmbType.System.Exit.key) }.single()
                val logAttrs = log.attributes.toStringMap()
                assertEquals(sessionPartId, logAttrs[AEI_SESSION_PART_ID])
                assertEquals(userSessionId, logAttrs[AEI_USER_SESSION_ID])
            },
        )
    }


    @Ignore("missing: span link to session ended-in should include user session id as well")
    @Test
    fun `span stop queued before endUserSession but processed after carries the post-end user-session-id on its EndSession link`() {
        testRule.runTest(
            testCaseAction = {
                recordSession {
                    embrace.startSpan("my-span")
                    embrace.endUserSession()
                }
            },
            assertAction = {
                // TODO: assert span link contains user session ID
            },
        )
    }

    private fun Envelope<SessionPartPayload>.assertSessionIds(): SessionIds =
        checkNotNull(findSessionSpan().attributes).toMap().assertSessionIds()

    private fun Log.assertSessionIds(): SessionIds =
        checkNotNull(attributes).toMap().assertSessionIds()
}
