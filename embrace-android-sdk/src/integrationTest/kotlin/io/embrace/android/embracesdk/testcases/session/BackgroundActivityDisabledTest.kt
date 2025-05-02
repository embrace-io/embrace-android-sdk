package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.findEventsOfType
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getSessionId
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
import io.embrace.android.embracesdk.internal.payload.ApplicationState
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.android.embracesdk.testframework.assertions.assertMatches
import io.embrace.android.embracesdk.testframework.assertions.getLogsOfType
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
    val testRule: SdkIntegrationTestRule = SdkIntegrationTestRule()

    @Test
    fun `recording telemetry in the background when background activity is disabled does the right thing`() {
        var traceStopMs: Long = -1
        lateinit var trace: EmbraceSpan

        testRule.runTest(
            testCaseAction = {
                recordSession {
                    trace = checkNotNull(embrace.startSpan("test-trace"))
                }

                traceStopMs = clock.tick(100L)
                assertTrue(trace.stop())

                // Check what should and shouldn't be logged when there is no background activity and the app is in the background
                assertTrue(embrace.isStarted)
                assertTrue(embrace.currentSessionId.isNullOrBlank())
                assertTrue(embrace.deviceId.isNotBlank())
                assertNull(embrace.startSpan("test"))
                embrace.logError("error")

                embrace.addBreadcrumb("not-logged")
                clock.tick(10_000L)

                embrace.logInfo("info")

                recordSession {
                    assertFalse(embrace.currentSessionId.isNullOrBlank())
                    embrace.addBreadcrumb("logged")
                    embrace.logWarning("warning")
                    embrace.logError("sent-after-session")
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                getSessionEnvelopes(0, ApplicationState.BACKGROUND)

                val logs = getLogEnvelopes(2).flatMap { it.getLogsOfType(EmbType.System.Log) }
                with(logs[0]) {
                    assertEquals("error", body)
                    attributes?.assertMatches(
                        mapOf(
                            embState.attributeKey to "background"
                        )
                    )
                    assertNull(attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
                }
                with(logs[1]) {
                    assertEquals("info", body)
                    attributes?.assertMatches(
                        mapOf(
                            embState.attributeKey to "background"
                        )
                    )
                    assertNull(attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key))
                }
                with(logs[2]) {
                    assertEquals("warning", body)
                    attributes?.assertMatches(
                        mapOf(
                            embState.attributeKey to "foreground",
                            SessionIncubatingAttributes.SESSION_ID.key to sessions[1].getSessionId()
                        )
                    )
                }

                val secondSession = sessions[1]
                with(logs[3]) {
                    assertEquals("sent-after-session", body)
                    attributes?.assertMatches(
                        mapOf(
                            embState.attributeKey to "foreground",
                            SessionIncubatingAttributes.SESSION_ID.key to secondSession.getSessionId()
                        )
                    )
                }

                with(secondSession) {
                    with(findSessionSpan()) {
                        with(findEventsOfType(EmbType.System.Breadcrumb)) {
                            assertEquals(1, size)
                            single().attributes?.assertMatches(
                                mapOf(
                                    "message" to "logged"
                                )
                            )
                        }
                    }

                    with(checkNotNull(data.spans?.find { it.name == "test-trace" })) {
                        assertEquals(traceStopMs, endTimeNanos?.nanosToMillis())
                    }
                }
            }
        )
    }

    @Test
    fun `session span and payloads structurally correct`() {
        var session1StartMs: Long = -1
        var session1EndMs: Long = -1
        var session2StartMs: Long = -1
        var session2EndMs: Long = -1

        testRule.runTest(
            testCaseAction = {
                with(recordSession()) {
                    session1StartMs = startTimeMs
                    session1EndMs = endTimeMs
                }
                clock.tick(15000)
                with(recordSession()) {
                    session2StartMs = startTimeMs
                    session2EndMs = endTimeMs
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val session1 = sessions[0]
                val session2 = sessions[1]
                assertEquals(2, sessions.size)
                assertEquals(0, getSessionEnvelopes(0, ApplicationState.BACKGROUND).size)

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
                    sequenceId = 10,
                    coldStart = false,
                )

                assertNotEquals(
                    sessionSpan1.attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key),
                    sessionSpan2.attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key)
                )

                assertEquals(
                    sessionSpan1.attributes?.findAttributeValue(embProcessIdentifier.attributeKey),
                    sessionSpan2.attributes?.findAttributeValue(embProcessIdentifier.attributeKey)
                )
            }
        )
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
        attributes?.assertMatches(
            mapOf(
                embSessionNumber.attributeKey to sessionNumber,
                embSequenceId.attributeKey to sequenceId,
                embColdStart.attributeKey to coldStart,
                embState.attributeKey to "foreground",
                embCleanExit.attributeKey to "true",
                embTerminated.attributeKey to "false",
                embSessionStartType.attributeKey to "state",
                embSessionEndType.attributeKey to "state",
            )
        )
        with(checkNotNull(attributes)) {
            assertFalse(findAttributeValue(embProcessIdentifier.attributeKey).isNullOrBlank())
            assertFalse(findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key).isNullOrBlank())
        }
    }
}
