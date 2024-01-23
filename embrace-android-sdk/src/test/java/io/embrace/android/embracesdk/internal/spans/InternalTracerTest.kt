package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
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

    private lateinit var spansService: SpansServiceImpl
    private lateinit var internalTracer: InternalTracer
    private val clock = FakeClock(10000L)

    @Before
    fun setup() {
        spansService = SpansServiceImpl(
            sdkInitStartTimeNanos = 100L,
            clock = FakeOpenTelemetryClock(embraceClock = clock),
            telemetryService = FakeTelemetryService()
        )
        internalTracer = InternalTracer(EmbraceTracer(spansService))
        spansService.flushSpans()
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
        spansService.flushSpans()
        assertTrue(internalTracer.addSpanEvent(spanId = parentSpanId, "first event"))
        assertTrue(internalTracer.stopSpan(parentSpanId))
        assertFalse(internalTracer.addSpanEvent(spanId = parentSpanId, "second event"))
        with(verifyPublicSpan("parent-span")) {
            assertEquals(1, events.size)
            assertEquals("first event", events[0].name)
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
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L

        assertTrue(
            internalTracer.recordCompletedSpan(
                name = expectedName,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime
            )
        )

        with(verifyPublicSpan(expectedName)) {
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
        }
    }

    @Test
    fun `record completed span with bad event data`() {
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val eventsInput: List<Map<String, Any>> =
            listOf(
                mapOf("name" to "correct event", "timestampNanos" to 0L, "attributes" to mapOf("key" to "value")),
                mapOf("timestampNanos" to 0L, "attributes" to mapOf("key" to "value")),
                mapOf("name" to 1234),
                mapOf("name" to "failed event", "timestampNanos" to 123),
                mapOf("name" to "failed event", "timestampNanos" to "123"),
                mapOf("name" to "failed event", "attributes" to mapOf("key" to 123)),
                mapOf("name" to "failed event", "attributes" to mapOf(123 to "123")),
            )

        assertTrue(
            internalTracer.recordCompletedSpan(
                name = expectedName,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime,
                events = eventsInput
            )
        )

        with(verifyPublicSpan(expectedName)) {
            assertEquals(1, events.size)
            assertEquals("correct event", events[0].name)
        }
    }

    @Test
    fun `record completed failed span`() {
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L

        assertTrue(
            internalTracer.recordCompletedSpan(
                name = expectedName,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime,
                errorCode = ErrorCode.FAILURE
            )
        )

        with(verifyPublicSpan(expectedName, true, ErrorCode.FAILURE)) {
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
            assertEquals(StatusCode.ERROR, status)
        }
    }

    @Test
    fun `record completed child span`() {
        val parentSpanId = checkNotNull(internalTracer.startSpan(name = "parent-span"))
        val expectedName = "child-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L

        assertTrue(
            internalTracer.recordCompletedSpan(
                name = expectedName,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime,
                parentSpanId = parentSpanId
            )
        )

        with(verifyPublicSpan(expectedName, false)) {
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
        }
    }

    @Test
    fun `record completed span with all the fixings`() {
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val expectedType = EmbraceAttributes.Type.PERFORMANCE
        val expectedAttributes = mapOf(
            Pair("attribute1", "value1"),
            Pair("attribute2", "value2")
        )
        val expectedEvents: List<Map<String, Any>> =
            listOf(
                mapOf("name" to "event1", "timestampNanos" to 0L, "attributes" to expectedAttributes),
                mapOf("name" to "event2", "timestampNanos" to 5L, "attributes" to expectedAttributes),
            )

        assertTrue(
            internalTracer.recordCompletedSpan(
                name = expectedName,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime,
                attributes = expectedAttributes,
                events = expectedEvents
            )
        )

        with(verifyPublicSpan(expectedName)) {
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
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
                assertEquals(expectedEvents[0]["timestampNanos"], timestampNanos)
                assertEquals(2, attributes.size)
            }
            with(events[1]) {
                assertEquals(expectedEvents[1]["name"], name)
                assertEquals(expectedEvents[1]["timestampNanos"], timestampNanos)
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
        assertNull(spansService.getSpan(spanId = NON_EXISTENT_SPAN_ID))
        assertFalse(
            internalTracer.recordCompletedSpan(
                name = "test-span",
                parentSpanId = NON_EXISTENT_SPAN_ID,
                startTimeNanos = 0L,
                endTimeNanos = 10L
            )
        )
    }

    private fun verifyPublicSpan(name: String, traceRoot: Boolean = true, errorCode: ErrorCode? = null): EmbraceSpanData {
        val currentSpans = spansService.completedSpans()
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
