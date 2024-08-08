package io.embrace.android.embracesdk.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.findEventsOfType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getSentBackgroundActivities
import io.embrace.android.embracesdk.getSentLogPayloads
import io.embrace.android.embracesdk.getSentLogs
import io.embrace.android.embracesdk.getSentSessions
import io.embrace.android.embracesdk.getSessionId
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.opentelemetry.embCleanExit
import io.embrace.android.embracesdk.internal.opentelemetry.embColdStart
import io.embrace.android.embracesdk.internal.opentelemetry.embProcessIdentifier
import io.embrace.android.embracesdk.internal.opentelemetry.embSequenceId
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionEndType
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.internal.opentelemetry.embSessionStartType
import io.embrace.android.embracesdk.internal.opentelemetry.embState
import io.embrace.android.embracesdk.internal.opentelemetry.embTerminated
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.worker.WorkerName
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verify functionality of the SDK if background activities are disabled
 */
@RunWith(AndroidJUnit4::class)
internal class BackgroundActivityDisabledTest {

    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule {
        val clock = FakeClock()
        val initModule = FakeInitModule(clock)
        val workerThreadModule = FakeWorkerThreadModule(initModule, WorkerName.REMOTE_LOGGING)

        IntegrationTestRule.Harness(
            overriddenClock = clock,
            overriddenInitModule = initModule,
            overriddenWorkerThreadModule = workerThreadModule,
        ).apply {
            overriddenConfigService.backgroundActivityCaptureEnabled = false
        }
    }

    @Test
    fun `recording telemetry in the background when background activity is disabled does the right thing`() {
        with(testRule) {
            lateinit var trace: EmbraceSpan
            harness.recordSession {
                trace = checkNotNull(embrace.startSpan("test-trace"))
            }
            runLoggingThread()

            val traceStopMs = harness.overriddenClock.tick(100L)
            assertTrue(trace.stop())

            // Check what should and shouldn't be logged when there is no background activity and the app is in the background
            assertTrue(embrace.isStarted)
            assertTrue(embrace.currentSessionId.isNullOrBlank())
            assertTrue(embrace.getDeviceId().isNotBlank())
            assertNull(embrace.startSpan("test"))
            embrace.logError("error")
            runLoggingThread()
            harness.overriddenClock.tick(2000L)
            flushLogBatch()

            embrace.addBreadcrumb("not-logged")
            harness.overriddenClock.tick(10_000L)
            with(checkNotNull(harness.getSentLogPayloads(1).single().data.logs?.single())) {
                assertEquals("error", body)
                assertEquals("background", attributes?.findAttributeValue(embState.attributeKey.key))
                assertNull(attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
            }
            embrace.logInfo("info")
            runLoggingThread()

            val session = harness.recordSession {
                assertFalse(embrace.currentSessionId.isNullOrBlank())
                embrace.addBreadcrumb("logged")
                embrace.logWarning("warning")
                runLoggingThread()
                harness.overriddenClock.tick(2000L)
                flushLogBatch()

                with(checkNotNull(harness.getSentLogPayloads(2).last().data.logs)) {
                    assertEquals(2, size)

                    // A log recorded when there's no session should still be sent, but without session ID
                    val infoLog = checkNotNull(find { it.body == "info" })
                    with(infoLog) {
                        assertEquals("background", attributes?.findAttributeValue(embState.attributeKey.key))
                        assertNull(attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
                    }

                    val warningLog = checkNotNull(find { it.body == "warning" })
                    with(warningLog) {
                        assertEquals("foreground", attributes?.findAttributeValue(embState.attributeKey.key))
                        assertEquals(embrace.currentSessionId, attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
                    }
                }
                embrace.logError("sent-after-session")
                runLoggingThread()
            }

            assertEquals(0, harness.getSentBackgroundActivities().size)
            checkNotNull(session)

            flushLogBatch()

            with(checkNotNull(harness.getSentLogs(1)?.single())) {
                assertEquals("sent-after-session", body)
                assertEquals("foreground", attributes?.findAttributeValue(embState.attributeKey.key))
                assertEquals(session.getSessionId(), attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
            }

            with(session) {
                with(findSessionSpan()) {
                    with(findEventsOfType(EmbType.System.Breadcrumb)) {
                        assertEquals(1, size)
                        assertEquals("logged", single().attributes?.findAttributeValue("message"))
                    }
                }

                with(checkNotNull(data.spans?.find { it.name == "test-trace" })) {
                    assertEquals(traceStopMs, endTimeNanos?.nanosToMillis())
                }
            }
        }
    }

    @Test
    fun `session span and payloads structurally correct`() {
        with(testRule) {
            val session1StartMs = harness.overriddenClock.now()
            harness.overriddenClock.tick(500L)
            val session1 = checkNotNull(harness.recordSession())
            val session1EndMs = harness.overriddenClock.now()
            val session2StartMs = harness.overriddenClock.tick(15000)
            val session2 = checkNotNull(harness.recordSession())
            val session2EndMs = harness.overriddenClock.now()
            assertEquals(2, harness.getSentSessions().size)
            assertEquals(0, harness.getSentBackgroundActivities().size)

            assertEquals(session1.metadata, session2.metadata)
            assertEquals(
                session1.resource?.copy(screenResolution = null, jailbroken = null),
                session2.resource?.copy(screenResolution = null, jailbroken = null)
            )
            assertEquals(session1.version, session2.version)
            assertEquals(session1.type, session2.type)

            val sessionSpan1 = session1.findSessionSpan()
            val sessionSpan2 = session2.findSessionSpan()
            sessionSpan1.assertExpectedSessionSpanAttributes(
                startMs = session1StartMs,
                endMs = session1EndMs,
                sessionNumber = 1,
                sequenceId = 1,
                coldStart = true,
            )

            sessionSpan2.assertExpectedSessionSpanAttributes(
                startMs = session2StartMs,
                endMs = session2EndMs,
                sessionNumber = 2,
                sequenceId = 6,
                coldStart = false,
            )

            assertNotEquals(
                sessionSpan1.attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key),
                sessionSpan2.attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key)
            )

            assertEquals(
                sessionSpan1.attributes?.findAttributeValue(embProcessIdentifier.attributeKey.key),
                sessionSpan2.attributes?.findAttributeValue(embProcessIdentifier.attributeKey.key)
            )
        }
    }

    private fun runLoggingThread() {
        (testRule.harness.overriddenWorkerThreadModule as FakeWorkerThreadModule).executor.runCurrentlyBlocked()
    }

    private fun flushLogBatch() {
        testRule.bootstrapper.logModule.logOrchestrator.flush(false)
    }

    private fun Span.assertExpectedSessionSpanAttributes(
        startMs: Long,
        endMs: Long,
        sessionNumber: Int,
        sequenceId: Int,
        coldStart: Boolean,
    ) {
        assertEquals(startMs, startTimeNanos?.nanosToMillis())
        assertEquals(endMs, endTimeNanos?.nanosToMillis())
        with(checkNotNull(attributes)) {
            assertEquals(sessionNumber.toString(), findAttributeValue(embSessionNumber.attributeKey.key))
            assertEquals(sequenceId.toString(), findAttributeValue(embSequenceId.attributeKey.key))
            assertEquals(coldStart.toString(), findAttributeValue(embColdStart.attributeKey.key))
            assertEquals("foreground", findAttributeValue(embState.attributeKey.key))
            assertEquals("true", findAttributeValue(embCleanExit.attributeKey.key))
            assertEquals("false", findAttributeValue(embTerminated.attributeKey.key))
            assertEquals("state", findAttributeValue(embSessionStartType.attributeKey.key))
            assertEquals("state", findAttributeValue(embSessionEndType.attributeKey.key))
            listOf(
                SessionIncubatingAttributes.SESSION_ID,
                embProcessIdentifier.attributeKey,
            ).forEach {
                assertFalse(findAttributeValue(it.key).isNullOrBlank())
            }
        }
    }
}
