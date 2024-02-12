package io.embrace.android.embracesdk.testcases

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.getSentBackgroundActivities
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [TIRAMISU])
@RunWith(AndroidJUnit4::class)
internal class TracingApiTest {
    @Rule
    @JvmField
    val testRule: IntegrationTestRule = IntegrationTestRule(
        harnessSupplier = {
            IntegrationTestRule.newHarness(startImmediately = false)
        }
    )

    @Test
    fun `check spans logged in the right session when service is initialized after a session starts`() {
        val testStartTime = testRule.harness.fakeClock.now()
        val spanExporter = FakeSpanExporter()
        with(testRule) {
            harness.fakeClock.tick(100L)
            embrace.addSpanExporter(spanExporter)
            embrace.start(harness.fakeCoreModule.context)
            harness.recordSession {
                val parentSpan = checkNotNull(embrace.createSpan(name = "test-trace-root"))
                assertTrue(parentSpan.start())
                assertTrue(parentSpan.addAttribute("oMg", "OmG"))
                assertSame(parentSpan, embrace.getSpan(checkNotNull(parentSpan.spanId)))
                assertTrue(embrace.recordSpan(name = "record-span-span", parent = parentSpan) {
                    harness.fakeClock.tick(100L)
                    parentSpan.addEvent("parent event")
                    true
                })
                val failedOpStartTime = embrace.internalInterface.getSdkCurrentTime().millisToNanos()
                harness.fakeClock.tick(200L)
                parentSpan.addEvent(name = "delayed event", timeNanos = (harness.fakeClock.now() - 50L).millisToNanos(), null)
                val failedOpEndTime = embrace.internalInterface.getSdkCurrentTime().millisToNanos()

                assertTrue(parentSpan.stop())

                val attributes = mapOf(
                    Pair(TOO_LONG_ATTRIBUTE_KEY, "value"),
                    Pair("test-attr", "false"),
                    Pair("myKey", TOO_LONG_ATTRIBUTE_VALUE)
                )

                val events = listOf(
                    checkNotNull(
                        EmbraceSpanEvent.create(
                            name = "failure time",
                            timestampNanos = failedOpEndTime,
                            attributes = mapOf(
                                Pair("retry", "1")
                            )
                        )
                    )
                )
                assertTrue(
                    embrace.recordCompletedSpan(
                        name = "completed-span",
                        startTimeNanos = failedOpStartTime,
                        endTimeNanos = failedOpEndTime,
                        errorCode = ErrorCode.FAILURE,
                        parent = parentSpan,
                        attributes = attributes,
                        events = events
                    )
                )
                harness.fakeClock.tick(300L)
                embrace.endAppStartup()
                // There should be at least 5 spans exported at this point, so we wait for the startup moment span to finish up.
                spanExporter.awaitSpanExport(count = 5)
            }
            val sessionEndTime = harness.fakeClock.now()
            assertEquals(2, harness.fakeDeliveryModule.deliveryService.lastSentSessions.size)
            val sessionMessage = harness.fakeDeliveryModule.deliveryService.lastSentSessions[1]
            assertEquals(SessionSnapshotType.NORMAL_END, sessionMessage.second)
            val allSpans = getSdkInitSpanFromBackgroundActivity() + checkNotNull(sessionMessage.first.spans)

            // There should be 6 total spans here, with the 5 from before the session ended, plus the session span
            assertEquals("The session span was not found?", 6, allSpans.size)
            val spansMap = allSpans.associateBy { it.name }
            val sessionSpan = checkNotNull(spansMap["emb-session-span"])
            val traceRootSpan = checkNotNull(spansMap["test-trace-root"])
            assertEmbraceSpanData(
                span = spansMap["emb-sdk-init"],
                expectedStartTimeMs = testStartTime + 100,
                expectedEndTimeMs = testStartTime + 100,
                expectedParentId = SpanId.getInvalid(),
                private = true,
                key = true
            )
            assertEmbraceSpanData(
                span = spansMap["emb-startup-moment"],
                expectedStartTimeMs = testStartTime + 100,
                expectedEndTimeMs = testStartTime + 700,
                expectedParentId = SpanId.getInvalid(),
                key = true
            )
            assertEmbraceSpanData(
                span = traceRootSpan,
                expectedStartTimeMs = testStartTime + 100,
                expectedEndTimeMs = testStartTime + 400,
                expectedParentId = SpanId.getInvalid(),
                expectedCustomAttributes = mapOf(Pair("oMg", "OmG")),
                expectedEvents = listOf(
                    checkNotNull(
                        EmbraceSpanEvent.create(
                            name = "parent event",
                            timestampNanos = (testStartTime + 200).millisToNanos(),
                            attributes = null
                        )
                    ),
                    checkNotNull(
                        EmbraceSpanEvent.create(
                            name = "delayed event",
                            timestampNanos = (testStartTime + 350).millisToNanos(),
                            attributes = null
                        ),
                    )
                ),
                key = true
            )
            assertEmbraceSpanData(
                span = spansMap["record-span-span"],
                expectedStartTimeMs = testStartTime + 100,
                expectedEndTimeMs = testStartTime + 200,
                expectedParentId = traceRootSpan.spanId,
                expectedTraceId = traceRootSpan.traceId
            )

            assertEmbraceSpanData(
                span = spansMap["completed-span"],
                expectedStartTimeMs = testStartTime + 200,
                expectedEndTimeMs = testStartTime + 400,
                expectedParentId = traceRootSpan.spanId,
                expectedTraceId = traceRootSpan.traceId,
                expectedStatus = StatusCode.ERROR,
                expectedCustomAttributes = mapOf(Pair("test-attr", "false")),
                expectedEvents = listOf(
                    checkNotNull(
                        EmbraceSpanEvent.create(
                            name = "failure time",
                            timestampNanos = (testStartTime + 400).millisToNanos(),
                            attributes = mapOf(
                                Pair("retry", "1")
                            )
                        )
                    )
                )
            )
            assertEmbraceSpanData(
                span = sessionSpan,
                expectedStartTimeMs = testStartTime + 100,
                expectedEndTimeMs = sessionEndTime,
                expectedParentId = SpanId.getInvalid(),
                private = true
            )
        }
    }

    private fun getSdkInitSpanFromBackgroundActivity(): List<EmbraceSpanData> {
        val lastSentBackgroundActivity = testRule.harness.getSentBackgroundActivities().last()
        return lastSentBackgroundActivity.spans?.filter { it.name == "emb-sdk-init" } ?: emptyList()
    }
}