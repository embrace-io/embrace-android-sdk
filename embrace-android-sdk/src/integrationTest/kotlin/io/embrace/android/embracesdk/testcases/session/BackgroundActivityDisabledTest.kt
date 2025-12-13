package io.embrace.android.embracesdk.testcases.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.assertions.assertMatches
import io.embrace.android.embracesdk.assertions.findEventsOfType
import io.embrace.android.embracesdk.assertions.findSessionSpan
import io.embrace.android.embracesdk.assertions.getLogsOfType
import io.embrace.android.embracesdk.assertions.getSessionId
import io.embrace.android.embracesdk.internal.arch.attrs.embCleanExit
import io.embrace.android.embracesdk.internal.arch.attrs.embColdStart
import io.embrace.android.embracesdk.internal.arch.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.arch.attrs.embSequenceId
import io.embrace.android.embracesdk.internal.arch.attrs.embSessionEndType
import io.embrace.android.embracesdk.internal.arch.attrs.embSessionNumber
import io.embrace.android.embracesdk.internal.arch.attrs.embSessionStartType
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.attrs.embTerminated
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.testframework.SdkIntegrationTestRule
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
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
@OptIn(IncubatingApi::class)
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
                getSessionEnvelopes(0, AppState.BACKGROUND)

                val logs = getLogEnvelopes(2).flatMap { it.getLogsOfType(EmbType.System.Log) }
                with(logs[0]) {
                    assertEquals("error", body)
                    attributes?.assertMatches(
                        mapOf(
                            embState.name to "background"
                        )
                    )
                    assertNull(attributes?.findAttributeValue(SessionAttributes.SESSION_ID))
                }
                with(logs[1]) {
                    assertEquals("info", body)
                    attributes?.assertMatches(
                        mapOf(
                            embState.name to "background"
                        )
                    )
                    assertNull(attributes?.findAttributeValue(SessionAttributes.SESSION_ID))
                }
                with(logs[2]) {
                    assertEquals("warning", body)
                    attributes?.assertMatches(
                        mapOf(
                            embState.name to "foreground",
                            SessionAttributes.SESSION_ID to sessions[1].getSessionId()
                        )
                    )
                }

                val secondSession = sessions[1]
                with(logs[3]) {
                    assertEquals("sent-after-session", body)
                    attributes?.assertMatches(
                        mapOf(
                            embState.name to "foreground",
                            SessionAttributes.SESSION_ID to secondSession.getSessionId()
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
                with(recordSession(isBackgroundActivityEnabled = false)) {
                    session1StartMs = startTimeMs
                    session1EndMs = endTimeMs
                }
                clock.tick(15000)
                with(recordSession(isBackgroundActivityEnabled = false)) {
                    session2StartMs = startTimeMs
                    session2EndMs = endTimeMs
                }
            },
            assertAction = {
                val sessions = getSessionEnvelopes(2)
                val session1 = sessions[0]
                val session2 = sessions[1]
                assertEquals(2, sessions.size)
                assertEquals(0, getSessionEnvelopes(0, AppState.BACKGROUND).size)

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
                    sessionSpan1.attributes?.findAttributeValue(SessionAttributes.SESSION_ID),
                    sessionSpan2.attributes?.findAttributeValue(SessionAttributes.SESSION_ID)
                )

                assertEquals(
                    sessionSpan1.attributes?.findAttributeValue(embProcessIdentifier.name),
                    sessionSpan2.attributes?.findAttributeValue(embProcessIdentifier.name)
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
                embSessionNumber.name to sessionNumber,
                embSequenceId.name to sequenceId,
                embColdStart.name to coldStart,
                embState.name to "foreground",
                embCleanExit.name to "true",
                embTerminated.name to "false",
                embSessionStartType.name to "state",
                embSessionEndType.name to "state",
            )
        )
        with(checkNotNull(attributes)) {
            assertFalse(findAttributeValue(embProcessIdentifier.name).isNullOrBlank())
            assertFalse(findAttributeValue(SessionAttributes.SESSION_ID).isNullOrBlank())
        }
    }
}
