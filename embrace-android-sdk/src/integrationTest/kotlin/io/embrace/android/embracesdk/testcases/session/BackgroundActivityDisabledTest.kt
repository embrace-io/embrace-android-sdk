package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.findEventsOfType
import io.embrace.android.embracesdk.findSessionSpan
import io.embrace.android.embracesdk.getLastLog
import io.embrace.android.embracesdk.getSentBackgroundActivities
import io.embrace.android.embracesdk.getSentLogPayloads
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
import io.embrace.android.embracesdk.internal.worker.Worker
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
        val workerThreadModule = FakeWorkerThreadModule(initModule, Worker.Background.LogMessageWorker)

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
        var traceStopMs: Long = -1
        with(testRule.action) {
            lateinit var trace: EmbraceSpan
            recordSession {
                trace = checkNotNull(embrace.startSpan("test-trace"))
            }
            runLoggingThread()

            traceStopMs = clock.tick(100L)
            assertTrue(trace.stop())

            // Check what should and shouldn't be logged when there is no background activity and the app is in the background
            assertTrue(embrace.isStarted)
            assertTrue(embrace.currentSessionId.isNullOrBlank())
            assertTrue(embrace.deviceId.isNotBlank())
            assertNull(embrace.startSpan("test"))
            embrace.logError("error")
            runLoggingThread()
            clock.tick(2000L)
            flushLogBatch()

            embrace.addBreadcrumb("not-logged")
            clock.tick(10_000L)
        }
        with(testRule) {
            with(checkNotNull(harness.getSentLogPayloads(1).single().data.logs).single()) {
                assertEquals("error", body)
                assertEquals(
                    "background",
                    attributes?.findAttributeValue(embState.attributeKey.key)
                )
                assertNull(attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
            }
        }
        with(testRule.action) {
            embrace.logInfo("info")
            runLoggingThread()

            recordSession {
                assertFalse(embrace.currentSessionId.isNullOrBlank())
                embrace.addBreadcrumb("logged")
                embrace.logWarning("warning")
                runLoggingThread()
                clock.tick(2000L)
                flushLogBatch()

                with(checkNotNull(testRule.harness.getSentLogPayloads(2).last().data.logs)) {
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

            val session = testRule.harness.getSentSessions(2).last()
            assertEquals(0, testRule.harness.getSentBackgroundActivities(0).size)

            flushLogBatch()
            checkNotNull(testRule.harness.getSentLogPayloads(3).getLastLog()).run {
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
        val clock = testRule.action.clock
        val session1StartMs = clock.now()
        clock.tick(500L)
        var session1EndMs: Long = -1
        var session2StartMs: Long = -1
        var session2EndMs: Long = -1

        testRule.runTest(
            testCaseAction = {
                recordSession()
                session1EndMs = clock.now()
                session2StartMs = clock.tick(15000)
                recordSession()
                session2EndMs = clock.now()
            },
            assertAction = {
                val sessions = harness.getSentSessions(2)
                val session1 = sessions[0]
                val session2 = sessions[1]
                assertEquals(2, sessions.size)
                assertEquals(0, harness.getSentBackgroundActivities(0).size)

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
                    sequenceId = 4,
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
        )
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
