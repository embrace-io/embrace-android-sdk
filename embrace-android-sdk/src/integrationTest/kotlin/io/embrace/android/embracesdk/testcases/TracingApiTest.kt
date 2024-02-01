package io.embrace.android.embracesdk.testcases

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.returnIfConditionMet
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

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
        with(testRule) {
            harness.fakeClock.tick(100L)
            embrace.start(harness.fakeCoreModule.context)
            harness.recordSession {
                val parentSpan = checkNotNull(embrace.createSpan(name = "test-trace-root"))
                assertTrue(parentSpan.start())
                assertTrue(parentSpan.addAttribute("oMg", "OmG"))
                assertTrue(embrace.recordSpan(name = "record-span-span", parent = parentSpan) {
                    harness.fakeClock.tick(100L)
                    parentSpan.addEvent("parent event")
                    true
                })
                val failedOpStartTime = harness.fakeClock.now()
                harness.fakeClock.tick(200L)
                parentSpan.addEvent(name = "delayed event", time = harness.fakeClock.now() - 50L, null)
                val failedOpEndTime = harness.fakeClock.now()

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
                        startTimeNanos = TimeUnit.MILLISECONDS.toNanos(failedOpStartTime),
                        endTimeNanos = TimeUnit.MILLISECONDS.toNanos(failedOpEndTime),
                        errorCode = ErrorCode.FAILURE,
                        parent = parentSpan,
                        attributes = attributes,
                        events = events
                    )
                )
                harness.fakeClock.tick(300L)
                embrace.endAppStartup()
                val backgroundActivitySpansCount = getSdkInitSpanFromBackgroundActivity().size
                assertTrue(
                    returnIfConditionMet(desiredValueSupplier = { true }, waitTimeMs = 1000) {
                        checkNotNull(harness.initModule.spansSink.completedSpans()).size == (5 - backgroundActivitySpansCount)
                    }
                )
            }
            val sessionEndTime = harness.fakeClock.now()
            assertEquals(1, harness.fakeDeliveryModule.deliveryService.lastSentSessions.size)
            val sessionMessage = harness.fakeDeliveryModule.deliveryService.lastSentSessions[0]
            assertEquals(SessionSnapshotType.NORMAL_END, sessionMessage.second)
            val allSpans = getSdkInitSpanFromBackgroundActivity() + checkNotNull(sessionMessage.first.spans)
            assertEquals(6, allSpans.size)
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
                            timestampNanos = TimeUnit.MILLISECONDS.toNanos(testStartTime + 200),
                            attributes = null
                        )
                    ),
                    checkNotNull(
                        EmbraceSpanEvent.create(
                            name = "delayed event",
                            timestampNanos = TimeUnit.MILLISECONDS.toNanos(testStartTime + 350),
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
                            timestampNanos = testStartTime + 400,
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
        val sentBackgroundActivities = testRule.harness.fakeDeliveryModule.deliveryService.lastSentBackgroundActivities
        val lastSentBackgroundActivity = sentBackgroundActivities.last()
        return lastSentBackgroundActivity.spans?.filter { it.name == "emb-sdk-init" } ?: emptyList()
    }
}