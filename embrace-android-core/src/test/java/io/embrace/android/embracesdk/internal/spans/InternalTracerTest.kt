package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.assertError
import io.embrace.android.embracesdk.arch.assertIsTypePerformance
import io.embrace.android.embracesdk.arch.assertNotPrivateSpan
import io.embrace.android.embracesdk.arch.assertSuccessful
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class InternalTracerTest {
    private lateinit var spanSink: SpanSink
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spanService: SpanService
    private lateinit var internalTracer: InternalTracer
    private val clock = FakeClock()

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spanSink = initModule.openTelemetryModule.spanSink
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
        internalTracer = InternalTracer(
            initModule.openTelemetryModule.spanRepository,
            initModule.openTelemetryModule.embraceTracer,
            initModule.clock
        )
        spanSink.flushSpans()
    }

    @Test
    fun `start and stop trace with child span`() {
        val parentSpanId = checkNotNull(internalTracer.startSpan(name = "parent-span"))
        assertNotNull(parentSpanId)
        clock.tick(1L)
        val childStartTimeMs = clock.now()
        clock.tick(10L)
        val spanId =
            checkNotNull(
                internalTracer.startSpan(
                    name = "test-span",
                    parentSpanId = parentSpanId,
                    startTimeMs = childStartTimeMs
                )
            )
        assertNotNull(spanId)
        assertTrue(internalTracer.addSpanAttribute(spanId = spanId, key = "keyz", value = "valuez"))
        val childEndTimeMs = clock.now() - 1L
        assertTrue(internalTracer.stopSpan(spanId = spanId, endTimeMs = childEndTimeMs))
        assertFalse(internalTracer.addSpanAttribute(spanId = spanId, key = "fail", value = "value"))
        with(verifyPublicSpan(name = "test-span")) {
            assertEquals("valuez", attributes["keyz"])
            assertEquals(childStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(childEndTimeMs, endTimeNanos.nanosToMillis())
        }
        spanSink.flushSpans()
        val firstEventTime = clock.now()
        clock.tick(10L)
        val secondEventTime = clock.now()
        assertTrue(
            internalTracer.addSpanEvent(
                spanId = parentSpanId,
                name = "first event",
                timestampMs = firstEventTime
            )
        )
        assertTrue(internalTracer.addSpanEvent(spanId = parentSpanId, name = "second event"))
        assertTrue(internalTracer.stopSpan(parentSpanId))
        assertFalse(internalTracer.addSpanEvent(spanId = parentSpanId, "failed event"))
        with(verifyPublicSpan("parent-span")) {
            assertEquals(2, events.size)
            assertEquals("first event", events[0].name)
            assertEquals(firstEventTime, events[0].timestampNanos.nanosToMillis())
            assertEquals(secondEventTime, events[1].timestampNanos.nanosToMillis())
        }
    }

    @Test
    fun `start and stop span with nanosecond timestamp`() {
        spanSink.flushSpans()
        val expectedStartTimeNanos = clock.now().millisToNanos()
        val spanId = checkNotNull(internalTracer.startSpan(name = "my-span", startTimeMs = expectedStartTimeNanos))
        clock.tick(10L)
        val expectedEndTimeNanos = clock.now().millisToNanos()
        assertTrue(internalTracer.stopSpan(spanId = spanId, endTimeMs = expectedEndTimeNanos))
        with(verifyPublicSpan("my-span")) {
            assertEquals(expectedStartTimeNanos, startTimeNanos)
            assertEquals(expectedEndTimeNanos, endTimeNanos)
        }
    }

    @Test
    fun `verify event timestamp fallback`() {
        spanSink.flushSpans()
        val spanId = checkNotNull(internalTracer.startSpan(name = "my-span"))
        val eventTimeNanos = clock.now().millisToNanos()
        clock.tick(10L)
        assertTrue(internalTracer.addSpanEvent(spanId = spanId, name = "first event", timestampMs = eventTimeNanos))
        assertTrue(internalTracer.stopSpan(spanId))
        with(verifyPublicSpan("my-span")) {
            assertEquals(1, events.size)
            assertEquals(eventTimeNanos, events[0].timestampNanos)
        }
    }

    @Test
    fun `record lambda running as span`() {
        val returnThis = 1881L
        val lambdaReturn = internalTracer.recordSpan(name = "lambda-test-span") {
            returnThis
        }
        verifyPublicSpan("lambda-test-span")
        assertEquals(returnThis, lambdaReturn)
    }

    @Test
    fun `record basic completed span`() {
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L

        assertTrue(
            internalTracer.recordCompletedSpan(
                name = expectedName,
                startTimeMs = expectedStartTimeMs,
                endTimeMs = expectedEndTimeMs
            )
        )

        with(verifyPublicSpan(expectedName)) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
        }
    }

    @Test
    fun `record completed span with various event add attempts`() {
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        clock.tick(10)
        val expectedEventStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L
        val eventsInput: List<Map<String, Any>> =
            listOf(
                mapOf(
                    "name" to "correct event",
                    "timestampMs" to expectedStartTimeMs,
                    "attributes" to mapOf("key" to "value")
                ),
                mapOf("name" to "correct event 2"),
                mapOf("name" to "correct fallback event", "timestampNanos" to expectedEndTimeMs.millisToNanos()),
                mapOf("timestampMs" to 0L, "attributes" to mapOf("key" to "value")),
                mapOf("name" to 1234),
                mapOf("name" to "failed event", "timestampMs" to 123),
                mapOf("name" to "failed event", "timestampMs" to "123"),
                mapOf("name" to "failed event", "timestampNanos" to 123),
                mapOf("name" to "failed event", "timestampNanos" to "123"),
                mapOf("name" to "partial event", "attributes" to mapOf("key" to 123)),
                mapOf("name" to "partial event2", "attributes" to mapOf(123 to "123")),
            )

        assertTrue(
            internalTracer.recordCompletedSpan(
                name = expectedName,
                startTimeMs = expectedStartTimeMs,
                endTimeMs = expectedEndTimeMs,
                events = eventsInput
            )
        )

        with(verifyPublicSpan(expectedName)) {
            assertEquals(5, events.size)
            assertEquals("correct event", events[0].name)
            assertEquals(expectedStartTimeMs, events[0].timestampNanos.nanosToMillis())
            assertEquals("correct event 2", events[1].name)
            assertEquals(expectedEventStartTimeMs, events[1].timestampNanos.nanosToMillis())
            assertEquals("correct fallback event", events[2].name)
            assertEquals(expectedEndTimeMs, events[2].timestampNanos.nanosToMillis())
            assertEquals("partial event", events[3].name)
            assertEquals(0, events[3].attributes.size)
            assertEquals("partial event2", events[4].name)
            assertEquals(0, events[4].attributes.size)
        }
    }

    @Test
    fun `record completed failed span`() {
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L

        assertTrue(
            internalTracer.recordCompletedSpan(
                name = expectedName,
                startTimeMs = expectedStartTimeMs,
                endTimeMs = expectedEndTimeMs,
                errorCode = ErrorCode.FAILURE
            )
        )

        with(verifyPublicSpan(expectedName, ErrorCode.FAILURE)) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
            assertTrue(status is StatusCode.Error)
        }
    }

    @Test
    fun `record completed child span`() {
        val parentSpanId = checkNotNull(internalTracer.startSpan(name = "parent-span"))
        val expectedName = "child-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L

        assertTrue(
            internalTracer.recordCompletedSpan(
                name = expectedName,
                startTimeMs = expectedStartTimeMs,
                endTimeMs = expectedEndTimeMs,
                parentSpanId = parentSpanId
            )
        )

        with(verifyPublicSpan(expectedName)) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
        }
    }

    @Test
    fun `record completed span with all the fixings`() {
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L
        val expectedAttributes = mapOf(
            Pair("attribute1", "value1"),
            Pair("attribute2", "value2")
        )
        val expectedEvents: List<Map<String, Any>> =
            listOf(
                mapOf("name" to "event1", "timestampMs" to 0L, "attributes" to expectedAttributes),
                mapOf("name" to "event2", "timestampMs" to 5L, "attributes" to expectedAttributes),
            )

        assertTrue(
            internalTracer.recordCompletedSpan(
                name = expectedName,
                startTimeMs = expectedStartTimeMs,
                endTimeMs = expectedEndTimeMs,
                attributes = expectedAttributes,
                events = expectedEvents
            )
        )

        with(verifyPublicSpan(expectedName)) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
            assertIsTypePerformance()
            expectedAttributes.forEach {
                assertEquals(it.value, attributes[it.key])
            }
            with(events[0]) {
                assertEquals(expectedEvents[0]["name"], name)
                assertEquals(expectedEvents[0]["timestampMs"], timestampNanos.nanosToMillis())
                assertEquals(2, attributes.size)
            }
            with(events[1]) {
                assertEquals(expectedEvents[1]["name"], name)
                assertEquals(expectedEvents[1]["timestampMs"], timestampNanos.nanosToMillis())
                assertEquals(2, attributes.size)
            }
        }
    }

    @Test
    fun `trying to work with untracked spans returns expected results`() {
        assertNull(internalTracer.startSpan(name = "test-span", parentSpanId = NON_EXISTENT_SPAN_ID))
        assertFalse(internalTracer.stopSpan(spanId = NON_EXISTENT_SPAN_ID))
        assertFalse(internalTracer.addSpanAttribute(spanId = NON_EXISTENT_SPAN_ID, key = "key", value = "value"))
        assertFalse(internalTracer.addSpanEvent(spanId = NON_EXISTENT_SPAN_ID, name = "even1"))
        assertEquals(2, internalTracer.recordSpan(name = "test-span", parentSpanId = NON_EXISTENT_SPAN_ID) { 1 + 1 })
        assertFalse(
            internalTracer.recordCompletedSpan(
                name = "test-span",
                parentSpanId = NON_EXISTENT_SPAN_ID,
                startTimeMs = 0L,
                endTimeMs = 10L
            )
        )
    }

    private fun verifyPublicSpan(
        name: String,
        errorCode: ErrorCode? = null,
    ): EmbraceSpanData {
        val currentSpans = spanSink.completedSpans()
        assertEquals(1, currentSpans.size)
        val currentSpan = currentSpans[0]
        assertEquals(name, currentSpan.name)
        currentSpan.assertIsTypePerformance()
        if (errorCode == null) {
            currentSpan.assertSuccessful()
        } else {
            currentSpan.assertError(errorCode)
        }
        currentSpan.assertNotPrivateSpan()
        return currentSpan
    }

    companion object {
        private const val NON_EXISTENT_SPAN_ID = "9f70d2bc3b88f393"
    }
}
