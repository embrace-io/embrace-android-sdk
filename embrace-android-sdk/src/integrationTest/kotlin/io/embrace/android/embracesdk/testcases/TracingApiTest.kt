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
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
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

    private val results = mutableListOf<String>()

    @Before
    fun setup() {
        results.clear()
    }

    @Test
    fun `check spans logged in the right session when service is initialized after a session starts`() {
        val testStartTime = testRule.harness.fakeClock.now()
        val spanExporter = FakeSpanExporter()
        with(testRule) {
            harness.fakeClock.tick(100L)
            embrace.addSpanExporter(spanExporter)
            embrace.start(harness.fakeCoreModule.context)
            results.add("\nSpans exported before session starts: ${spanExporter.exportedSpans.toList().map { it.name }}")
            val sessionMessage = harness.recordSession {
                val parentSpan = checkNotNull(embrace.createSpan(name = "test-trace-root"))
                assertTrue(parentSpan.start(startTimeNanos = (harness.fakeClock.now() - 1L).millisToNanos()))
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
                val bonusSpan = checkNotNull(embrace.startSpan(name = "bonus-span", parent = parentSpan))
                assertTrue(bonusSpan.stop(endTimeNanos = (harness.fakeClock.now() + 1).millisToNanos()))
                harness.fakeClock.tick(300L)
                results.add("\nSpans exported before ending startup: ${spanExporter.exportedSpans.toList().map { it.name }}")
                embrace.endAppStartup()
            }
            results.add("\nSpans exported after session ends: ${spanExporter.exportedSpans.toList().map { it.name }}")
            assertTrue("Timed out waiting for the expected spans: $results", spanExporter.awaitSpanExport(8))
            val sessionEndTime = harness.fakeClock.now()
            assertEquals(2, harness.fakeDeliveryModule.deliveryService.lastSentSessions.size)
            val allSpans = getSdkInitSpanFromBackgroundActivity() +
                checkNotNull(sessionMessage?.spans) +
                checkNotNull(harness.openTelemetryModule.spanSink.completedSpans())

            val spansMap = allSpans.associateBy { it.name }
            val sessionSpan = checkNotNull(spansMap["emb-session-span"])
            val traceRootSpan = checkNotNull(spansMap["test-trace-root"])

            results.add("\nAll spans to validate: ${allSpans.map { it.name }}")
            results.add("\nSpans exported before validation: ${spanExporter.exportedSpans.toList().map { it.name }}")
            val expectedSpanName = listOf(
                "emb-sdk-init",
                "test-trace-root",
                "record-span-span",
                "completed-span",
                "emb-startup-moment",
                "emb-session-span",
                "bonus-span"
            )
            expectedSpanName.forEach {
                checkNotNull(spansMap[it]) { "$it not found: $results" }
            }

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
                expectedStartTimeMs = testStartTime + 99,
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
                span = spansMap["bonus-span"],
                expectedStartTimeMs = testStartTime + 400,
                expectedEndTimeMs = testStartTime + 401,
                expectedParentId = traceRootSpan.spanId,
                expectedTraceId = traceRootSpan.traceId
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