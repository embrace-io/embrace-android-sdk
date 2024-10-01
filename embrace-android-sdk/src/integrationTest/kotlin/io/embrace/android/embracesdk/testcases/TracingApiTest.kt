package io.embrace.android.embracesdk.testcases

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.IntegrationTestRule
import io.embrace.android.embracesdk.arch.assertIsTypePerformance
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_KEY
import io.embrace.android.embracesdk.fixtures.TOO_LONG_ATTRIBUTE_VALUE
import io.embrace.android.embracesdk.getSentBackgroundActivities
import io.embrace.android.embracesdk.getSingleSession
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.payload.SpanEvent
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.context.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    val testRule: IntegrationTestRule = IntegrationTestRule {
        IntegrationTestRule.Harness(startImmediately = false)
    }

    private val results = mutableListOf<String>()
    private lateinit var executor: SingleThreadTestScheduledExecutor

    @Before
    fun setup() {
        executor =  SingleThreadTestScheduledExecutor()
        results.clear()
    }

    @Test
    fun `check spans logged in the right session when service is initialized after a session starts`() {
        val testStartTimeMs = testRule.action.clock.now()
        val spanExporter = FakeSpanExporter()
        with(testRule.action) {
            clock.tick(100L)
            embrace.addSpanExporter(spanExporter)
            startSdk()
            results.add("\nSpans exported before session starts: ${spanExporter.exportedSpans.toList().map { it.name }}")
            recordSession {
                val parentSpan = checkNotNull(embrace.createSpan(name = "test-trace-root"))
                assertTrue(parentSpan.start(startTimeMs = clock.now() - 1L))
                assertTrue(parentSpan.addAttribute("oMg", "OmG"))
                assertSame(parentSpan, embrace.getSpan(checkNotNull(parentSpan.spanId)))
                assertTrue(embrace.recordSpan(name = "record-span-span", parent = parentSpan) {
                    clock.tick(100L)
                    parentSpan.addEvent("parent event")
                    parentSpan.addEvent(
                        name = "parent event with attributes and bad input time",
                        timestampMs = clock.now().millisToNanos(),
                        attributes = mapOf("key" to "value")
                    )
                    true
                })
                val failedOpStartTimeMs = embrace.internalInterface.getSdkCurrentTime()
                clock.tick(200L)
                parentSpan.addEvent(name = "delayed event", timestampMs = clock.now() - 50L, null)
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
                        startTimeMs = clock.now() + 10L
                    )
                )
                assertTrue(bonusSpan.stop(endTimeMs = clock.now() + 1))
                clock.tick(300L)
                assertTrue(bonusSpan2.stop())
                val unendingSpan = checkNotNull(embrace.startSpan("unending-span"))
                clock.tick(100L)
                unendingSpan.addAttribute("unending-key", "unending-value")
                unendingSpan.addEvent("unending-event")
                results.add("\nSpans exported before ending startup: ${spanExporter.exportedSpans.toList().map { it.name }}")
                embrace.endAppStartup()
            }
            val session = testRule.harness.getSingleSession()
            results.add("\nSpans exported after session ends: ${spanExporter.exportedSpans.toList().map { it.name }}")
            val sessionEndTime = clock.now()
            val allSpans = getSdkInitSpanFromBackgroundActivity() +
                checkNotNull(session.data.spans) +
                testRule.harness.overriddenOpenTelemetryModule.spanSink.completedSpans().map(EmbraceSpanData::toNewPayload)

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
                    SpanEvent(
                        name = "parent event",
                        timestampNanos = (testStartTimeMs + 200).millisToNanos(),
                        attributes = emptyList()
                    ),
                    SpanEvent(
                        name = "parent event with attributes and bad input time",
                        timestampNanos = (testStartTimeMs + 200).millisToNanos(),
                        attributes = listOf(Attribute("key", "value"))
                    ),
                    SpanEvent(
                        name = "delayed event",
                        timestampNanos = (testStartTimeMs + 350).millisToNanos(),
                        attributes = emptyList()
                    ),
                ),
                key = true
            )
            val expectedParentId = traceRootSpan.spanId
            assertEmbraceSpanData(
                span = spansMap["record-span-span"],
                expectedStartTimeMs = testStartTimeMs + 100,
                expectedEndTimeMs = testStartTimeMs + 200,
                expectedParentId = checkNotNull(expectedParentId),
                expectedTraceId = traceRootSpan.traceId
            )

            assertEmbraceSpanData(
                span = spansMap["completed-span"],
                expectedStartTimeMs = testStartTimeMs + 200,
                expectedEndTimeMs = testStartTimeMs + 400,
                expectedParentId = expectedParentId,
                expectedTraceId = traceRootSpan.traceId,
                expectedErrorCode = ErrorCode.FAILURE,
                expectedCustomAttributes = mapOf(Pair("test-attr", "false")),
                expectedEvents = listOf(
                    SpanEvent(
                        name = "failure time",
                        timestampNanos = (testStartTimeMs + 400).millisToNanos(),
                        attributes = listOf(Attribute("retry", "1"))
                    )
                )
            )

            assertEmbraceSpanData(
                span = spansMap["bonus-span"],
                expectedStartTimeMs = testStartTimeMs + 400,
                expectedEndTimeMs = testStartTimeMs + 401,
                expectedParentId = expectedParentId,
                expectedTraceId = traceRootSpan.traceId
            )

            assertEmbraceSpanData(
                span = spansMap["bonus-span-2"],
                expectedStartTimeMs = testStartTimeMs + 410,
                expectedEndTimeMs = testStartTimeMs + 700,
                expectedParentId = expectedParentId,
                expectedTraceId = traceRootSpan.traceId
            )

            assertEmbraceSpanData(
                span = sessionSpan,
                expectedStartTimeMs = testStartTimeMs + 100,
                expectedEndTimeMs = sessionEndTime,
                expectedParentId = SpanId.getInvalid(),
                private = false
            )

            val snapshots = checkNotNull(session.data.spanSnapshots).associateBy { it.name }
            val unendingSpanSnapshot = checkNotNull(snapshots["unending-span"])
            unendingSpanSnapshot.assertIsTypePerformance()
            assertEmbraceSpanData(
                span = unendingSpanSnapshot,
                expectedStartTimeMs = testStartTimeMs + 700,
                expectedEndTimeMs = null,
                expectedParentId = SpanId.getInvalid(),
                expectedStatus = Span.Status.UNSET,
                expectedCustomAttributes = mapOf(Pair("unending-key", "unending-value")),
                expectedEvents = listOf(
                    SpanEvent(
                        name = "unending-event",
                        timestampNanos = (testStartTimeMs + 800).millisToNanos(),
                        attributes = emptyList()
                    )
                ),
                key = true
            )
        }
    }

    @Test
    fun `span can be parented by a span created on a different thread`() {
        with(testRule.action) {
            startSdk()
            recordSession {
                val latch = CountDownLatch(1)
                val parentThreadId = Thread.currentThread().id
                var childThreadId: Long = -1L
                val parent = checkNotNull(embrace.startSpan("parent"))
                val currentContext = Context.current()
                var currentContext2: Context? = null
                executor.submit {
                    childThreadId = Thread.currentThread().id
                    currentContext2 = Context.current()
                    val child = checkNotNull(embrace.startSpan(name = "child", parent = parent))
                    assertTrue(child.stop())
                    latch.countDown()
                }
                latch.await(1, TimeUnit.SECONDS)
                assertTrue(parent.stop())
                assertNotEquals(-1L, childThreadId)
                assertNotEquals(parentThreadId, childThreadId)
                assertEquals(currentContext, currentContext2)
            }
            val session = testRule.harness.getSingleSession()
            val spans = checkNotNull(session.data.spans).associateBy { it.name }
            val parentSpan = checkNotNull(spans["parent"])
            val childSpan = checkNotNull(spans["child"])
            assertEquals(parentSpan.traceId, childSpan.traceId)
            assertEquals(parentSpan.spanId, childSpan.parentSpanId)
        }
    }

    @Test
    fun `can only create span if there is a valid session`() {
        with(testRule.action) {
            configService.backgroundActivityCaptureEnabled = false
            assertNull(embrace.startSpan("test"))
            startSdk()
            recordSession {
                assertNotNull(embrace.startSpan("test"))
            }
            assertNull(embrace.startSpan("test"))
            recordSession {
                assertNotNull(embrace.startSpan("test"))
            }
        }
    }

    private fun getSdkInitSpanFromBackgroundActivity(): List<Span> {
        val lastSentBackgroundActivity = testRule.harness.getSentBackgroundActivities(1).last()
        val spans = checkNotNull(lastSentBackgroundActivity.data.spans)
        return spans.filter { it.name == "emb-sdk-init" }
    }
}