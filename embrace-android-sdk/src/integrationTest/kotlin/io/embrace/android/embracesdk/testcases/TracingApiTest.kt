package io.embrace.android.embracesdk.testcases

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.assertIsType
import io.embrace.android.embracesdk.arch.assertIsTypePerformance
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.assertions.assertSpanPayload
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.getSentBackgroundActivities
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.recordSession
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
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
        val testStartTimeMs = testRule.harness.fakeClock.now()
        val spanExporter = FakeSpanExporter()
        with(testRule) {
            harness.fakeClock.tick(100L)
            embrace.addSpanExporter(spanExporter)
            embrace.start(harness.fakeCoreModule.context)
            results.add("\nSpans exported before session starts: ${spanExporter.exportedSpans.toList().map { it.name }}")
            val sessionMessage = harness.recordSession {
                val parentSpan = checkNotNull(embrace.createSpan(name = "test-trace-root"))
                assertTrue(parentSpan.start(startTimeMs = harness.fakeClock.now() - 1L))
                assertTrue(parentSpan.addAttribute("oMg", "OmG"))
                assertSame(parentSpan, embrace.getSpan(checkNotNull(parentSpan.spanId)))
                assertTrue(embrace.recordSpan(name = "record-span-span", parent = parentSpan) {
                    harness.fakeClock.tick(100L)
                    parentSpan.addEvent("parent event")
                    parentSpan.addEvent(
                        name = "parent event with attributes and bad input time",
                        timestampMs = harness.fakeClock.now().millisToNanos(),
                        attributes = mapOf("key" to "value")
                    )
                    true
                })
                val failedOpStartTimeMs = embrace.internalInterface.getSdkCurrentTime()
                harness.fakeClock.tick(200L)
                parentSpan.addEvent(name = "delayed event", timestampMs = harness.fakeClock.now() - 50L, null)
                val failedOpEndTimeMs = embrace.internalInterface.getSdkCurrentTime()

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
                            timestampMs = failedOpEndTimeMs,
                            attributes = mapOf(
                                Pair("retry", "1")
                            )
                        )
                    )
                )
                assertTrue(
                    embrace.recordCompletedSpan(
                        name = "completed-span",
                        startTimeMs = failedOpStartTimeMs.millisToNanos(),
                        endTimeMs = failedOpEndTimeMs.millisToNanos(),
                        errorCode = ErrorCode.FAILURE,
                        parent = parentSpan,
                        attributes = attributes,
                        events = events
                    )
                )
                val bonusSpan = checkNotNull(embrace.startSpan(name = "bonus-span", parent = parentSpan))
                val bonusSpan2 = checkNotNull(
                    embrace.startSpan(
                        name = "bonus-span-2",
                        parent = parentSpan,
                        startTimeMs = harness.fakeClock.now() + 10L
                    )
                )
                assertTrue(bonusSpan.stop(endTimeMs = harness.fakeClock.now() + 1))
                harness.fakeClock.tick(300L)
                assertTrue(bonusSpan2.stop())
                val unendingSpan = checkNotNull(embrace.startSpan("unending-span"))
                harness.fakeClock.tick(100L)
                unendingSpan.addAttribute("unending-key", "unending-value")
                unendingSpan.addEvent("unending-event")
                results.add("\nSpans exported before ending startup: ${spanExporter.exportedSpans.toList().map { it.name }}")
                embrace.endAppStartup()
            }
            results.add("\nSpans exported after session ends: ${spanExporter.exportedSpans.toList().map { it.name }}")
            val sessionEndTime = harness.fakeClock.now()
            val session = checkNotNull(sessionMessage)
            val allSpans = getSdkInitSpanFromBackgroundActivity() +
                checkNotNull(session.spans) +
                harness.openTelemetryModule.spanSink.completedSpans()

            val spansMap = allSpans.associateBy { it.name }
            val sessionSpan = checkNotNull(spansMap["emb-session"])
            val traceRootSpan = checkNotNull(spansMap["test-trace-root"])

            results.add("\nAll spans to validate: ${allSpans.map { it.name }}")
            results.add("\nSpans exported before validation: ${spanExporter.exportedSpans.toList().map { it.name }}")
            val expectedSpanName = listOf(
                "emb-sdk-init",
                "test-trace-root",
                "record-span-span",
                "completed-span",
                "emb-session",
                "bonus-span",
                "bonus-span-2",
            )
            expectedSpanName.forEach {
                checkNotNull(spansMap[it]) { "$it not found: $results" }
            }

            assertEmbraceSpanData(
                span = spansMap["emb-sdk-init"],
                expectedStartTimeMs = testStartTimeMs + 100,
                expectedEndTimeMs = testStartTimeMs + 100,
                expectedParentId = SpanId.getInvalid(),
                private = true,
                key = true
            )
            assertEmbraceSpanData(
                span = traceRootSpan,
                expectedStartTimeMs = testStartTimeMs + 99,
                expectedEndTimeMs = testStartTimeMs + 400,
                expectedParentId = SpanId.getInvalid(),
                expectedCustomAttributes = mapOf(Pair("oMg", "OmG")),
                expectedEvents = listOf(
                    checkNotNull(
                        EmbraceSpanEvent.create(
                            name = "parent event",
                            timestampMs = testStartTimeMs + 200,
                            attributes = null
                        )
                    ),
                    checkNotNull(
                        EmbraceSpanEvent.create(
                            name = "parent event with attributes and bad input time",
                            timestampMs = testStartTimeMs + 200,
                            attributes = mapOf("key" to "value")
                        )
                    ),
                    checkNotNull(
                        EmbraceSpanEvent.create(
                            name = "delayed event",
                            timestampMs = testStartTimeMs + 350,
                            attributes = null
                        ),
                    )
                ),
                key = true
            )
            assertEmbraceSpanData(
                span = spansMap["record-span-span"],
                expectedStartTimeMs = testStartTimeMs + 100,
                expectedEndTimeMs = testStartTimeMs + 200,
                expectedParentId = traceRootSpan.spanId,
                expectedTraceId = traceRootSpan.traceId
            )

            assertEmbraceSpanData(
                span = spansMap["completed-span"],
                expectedStartTimeMs = testStartTimeMs + 200,
                expectedEndTimeMs = testStartTimeMs + 400,
                expectedParentId = traceRootSpan.spanId,
                expectedTraceId = traceRootSpan.traceId,
                expectedErrorCode = ErrorCode.FAILURE,
                expectedCustomAttributes = mapOf(Pair("test-attr", "false")),
                expectedEvents = listOf(
                    checkNotNull(
                        EmbraceSpanEvent.create(
                            name = "failure time",
                            timestampMs = testStartTimeMs + 400,
                            attributes = mapOf(
                                Pair("retry", "1")
                            )
                        )
                    )
                )
            )

            assertEmbraceSpanData(
                span = spansMap["bonus-span"],
                expectedStartTimeMs = testStartTimeMs + 400,
                expectedEndTimeMs = testStartTimeMs + 401,
                expectedParentId = traceRootSpan.spanId,
                expectedTraceId = traceRootSpan.traceId
            )

            assertEmbraceSpanData(
                span = spansMap["bonus-span-2"],
                expectedStartTimeMs = testStartTimeMs + 410,
                expectedEndTimeMs = testStartTimeMs + 700,
                expectedParentId = traceRootSpan.spanId,
                expectedTraceId = traceRootSpan.traceId
            )

            assertEmbraceSpanData(
                span = sessionSpan,
                expectedStartTimeMs = testStartTimeMs + 100,
                expectedEndTimeMs = sessionEndTime,
                expectedParentId = SpanId.getInvalid(),
                private = true
            )

            assertEquals(2, checkNotNull(sessionMessage.spanSnapshots).size)
            val snapshots = sessionMessage.spanSnapshots.associateBy { it.name }
            val unendingSpanSnapshot = checkNotNull(snapshots["unending-span"])
            unendingSpanSnapshot.assertIsTypePerformance()
            unendingSpanSnapshot.assertSpanPayload(
                expectedStartTimeMs = testStartTimeMs + 700,
                expectedEndTimeMs = null,
                expectedParentId = null,
                expectedCustomAttributes = mapOf(Pair("unending-key", "unending-value")),
                expectedEvents = listOfNotNull(
                    EmbraceSpanEvent.create(
                        name = "unending-event",
                        timestampMs = testStartTimeMs + 800,
                        attributes = null
                    )?.toNewPayload()
                ),
                key = true
            )

            val sessionSpanSnapshot = checkNotNull(snapshots["emb-session"])
            sessionSpanSnapshot.assertIsType(EmbType.Ux.Session)
            sessionSpanSnapshot.assertSpanPayload(
                expectedStartTimeMs = sessionEndTime,
                expectedEndTimeMs = null,
                expectedParentId = null,
                private = true
            )
        }
    }

    private fun getSdkInitSpanFromBackgroundActivity(): List<EmbraceSpanData> {
        val lastSentBackgroundActivity = testRule.harness.getSentBackgroundActivities().last()
        return lastSentBackgroundActivity.spans?.filter { it.name == "emb-sdk-init" } ?: emptyList()
    }
}