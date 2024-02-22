package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
        )
        spanSink.flushSpans()
    }

    @Test
    fun `start and stop trace with child span`() {
        val parentSpanId = checkNotNull(internalTracer.startSpan(name = "parent-span"))
        assertNotNull(parentSpanId)
        val spanId = checkNotNull(internalTracer.startSpan(name = "test-span", parentSpanId = parentSpanId))
        assertNotNull(spanId)
        assertTrue(internalTracer.addSpanAttribute(spanId = spanId, key = "keyz", value = "valuez"))
        assertTrue(internalTracer.stopSpan(spanId))
        assertFalse(internalTracer.addSpanAttribute(spanId = spanId, key = "fail", value = "value"))
        with(verifyPublicSpan(name = "test-span", traceRoot = false)) {
            assertEquals("valuez", attributes["keyz"])
        }
        spanSink.flushSpans()
        val firstEventTime = clock.now()
        clock.tick(10L)
        val secondEventTime = clock.now()
        assertTrue(internalTracer.addSpanEvent(spanId = parentSpanId, name = "first event", timestampMs = firstEventTime))
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
    fun `verify event timestamp fallback`() {
        spanSink.flushSpans()
        val spanId = checkNotNull(internalTracer.startSpan(name = "my-span"))
        val eventTimeNanos = clock.nowInNanos()
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
    fun `record completed span with bad event data`() {
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L
        val eventsInput: List<Map<String, Any>> =
            listOf(
                mapOf("name" to "correct event", "timestampMs" to 0L, "attributes" to mapOf("key" to "value")),
                mapOf("name" to "correct event2"),
                mapOf("timestampMs" to 0L, "attributes" to mapOf("key" to "value")),
                mapOf("name" to 1234),
                mapOf("name" to "failed event", "timestampMs" to 123),
                mapOf("name" to "failed event", "timestampMs" to "123"),
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
            assertEquals(4, events.size)
            assertEquals("correct event", events[0].name)
            assertEquals(0L, events[0].timestampNanos.nanosToMillis())
            assertEquals("correct event2", events[1].name)
            assertEquals(expectedStartTimeMs, events[1].timestampNanos.nanosToMillis())
            assertEquals("partial event", events[2].name)
            assertEquals(0, events[2].attributes.size)
            assertEquals("partial event2", events[3].name)
            assertEquals(0, events[3].attributes.size)
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

        with(verifyPublicSpan(expectedName, true, ErrorCode.FAILURE)) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
            assertEquals(StatusCode.ERROR, status)
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

        with(verifyPublicSpan(expectedName, false)) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
        }
    }

    @Test
    fun `record completed span with all the fixings`() {
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L
        val expectedType = EmbraceAttributes.Type.PERFORMANCE
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
            assertEquals(
                expectedType.name,
                attributes[EmbraceAttributes.Type.PERFORMANCE.keyName()]
            )
            assertEquals("true", attributes["emb.key"])
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

    private fun verifyPublicSpan(name: String, traceRoot: Boolean = true, errorCode: ErrorCode? = null): EmbraceSpanData {
        val currentSpans = spanSink.completedSpans()
        assertEquals(1, currentSpans.size)
        val currentSpan = currentSpans[0]
        assertEquals(name, currentSpan.name)
        assertEquals(
            EmbraceAttributes.Type.PERFORMANCE.name,
            currentSpan.attributes[EmbraceAttributes.Type.PERFORMANCE.keyName()]
        )
        assertEquals(if (traceRoot) "true" else null, currentSpan.attributes["emb.key"])
        assertEquals(errorCode?.name, currentSpan.attributes[errorCode?.keyName()])
        assertFalse(currentSpan.isPrivate())
        return currentSpan
    }

    companion object {
        private const val NON_EXISTENT_SPAN_ID = "9f70d2bc3b88f393"
    }
}
