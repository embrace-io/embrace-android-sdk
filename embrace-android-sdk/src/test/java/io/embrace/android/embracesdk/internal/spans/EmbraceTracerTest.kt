package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fixtures.TOO_LONG_SPAN_NAME
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceTracerTest {
    private lateinit var spanRepository: SpanRepository
    private lateinit var spanSink: SpanSink
    private lateinit var spanService: SpanService
    private lateinit var embraceTracer: EmbraceTracer
    private val clock = FakeClock()

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        spanSink = initModule.openTelemetryModule.spanSink
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
        embraceTracer = initModule.openTelemetryModule.embraceTracer
        spanSink.flushSpans()
    }

    @Test
    fun `create and use EmbraceSpan using public interface`() {
        val embraceSpan = checkNotNull(embraceTracer.createSpan(name = "test-span"))
        assertNotNull(embraceSpan)
        assertTrue(embraceSpan.start())
        assertTrue(embraceSpan.stop())
        verifyPublicSpan("test-span")
    }

    @Test
    fun `start and stop EmbraceSpan with specific timestamps`() {
        val embraceSpan = checkNotNull(embraceTracer.createSpan(name = "test-span"))
        assertNotNull(embraceSpan)
        val expectedStartTimeMs = clock.now() - 1L
        val expectedEndTimeMs = clock.now() + 2L
        assertTrue(embraceSpan.start(startTimeMs = expectedStartTimeMs))
        assertTrue(embraceSpan.stop(endTimeMs = expectedEndTimeMs))
        with(verifyPublicSpan("test-span")) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
        }
    }

    @Test
    fun `stop EmbraceSpan with different error codes`() {
        ErrorCode.values().forEach { errorCode ->
            val embraceSpan = checkNotNull(embraceTracer.createSpan(name = "test-span"))
            assertNotNull(embraceSpan)
            assertTrue(embraceSpan.start())
            assertTrue(embraceSpan.stop(errorCode))
            verifyPublicSpan("test-span")
            spanSink.flushSpans()
        }
    }

    @Test
    fun `start a span directly`() {
        spanSink.flushSpans()
        val parent = checkNotNull(embraceTracer.startSpan(name = "test-span"))
        val child = checkNotNull(embraceTracer.startSpan(name = "child-span", parent))
        assertTrue(parent.stop())
        assertTrue(child.stop())
        assertEquals(2, spanSink.flushSpans().size)
    }

    @Test
    fun `cannot start a span if it was not created`() {
        assertNull(embraceTracer.startSpan(name = TOO_LONG_SPAN_NAME))
    }

    @Test
    fun `cannot start a span if given parent has not started`() {
        val notStartedParent = checkNotNull(embraceTracer.createSpan(name = "test-span"))
        assertNull(embraceTracer.startSpan(name = "child-span", notStartedParent))
    }

    @Test
    fun `record lambda running as span`() {
        val expectedAttributes = mapOf(
            Pair("attribute1", "value1"),
            Pair("attribute2", "value2")
        )

        val expectedEvents: List<EmbraceSpanEvent> =
            listOf(
                EmbraceSpanEvent(name = "event1", timestampNanos = 1_000_000L, expectedAttributes),
                EmbraceSpanEvent(name = "event2", timestampNanos = 5_000_000L, expectedAttributes),
            )

        val parentSpan = checkNotNull(embraceTracer.createSpan(name = "parent-span"))
        parentSpan.start()
        val returnThis = 1881L
        val expectedStartTimeMs = clock.now()
        val lambdaReturn = embraceTracer.recordSpan(
            name = "lambda-test-span",
            parent = parentSpan,
            attributes = expectedAttributes,
            events = expectedEvents
        ) {
            clock.tick(10)
            returnThis
        }
        val expectedEndTimeMs = clock.now()
        with(verifyPublicSpan(name = "lambda-test-span", traceRoot = false)) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
            expectedAttributes.forEach {
                assertEquals(it.value, attributes[it.key])
            }
            assertEquals(expectedEvents, events)
        }
        assertEquals(returnThis, lambdaReturn)
    }

    @Test
    fun `record basic completed span`() {
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L

        assertTrue(
            embraceTracer.recordCompletedSpan(
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
    fun `record basic failed span`() {
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L

        assertTrue(
            embraceTracer.recordCompletedSpan(
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
        val parentSpan = checkNotNull(embraceTracer.createSpan(name = "parent-span"))
        parentSpan.start()
        val expectedName = "child-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L

        assertTrue(
            embraceTracer.recordCompletedSpan(
                name = expectedName,
                startTimeMs = expectedStartTimeMs,
                endTimeMs = expectedEndTimeMs,
                parent = parentSpan
            )
        )

        with(verifyPublicSpan(expectedName, false)) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
        }
    }

    @Test
    fun `record completed abandoned child span`() {
        val parentSpan = checkNotNull(embraceTracer.createSpan(name = "parent-span"))
        parentSpan.start()
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L

        assertTrue(
            embraceTracer.recordCompletedSpan(
                name = expectedName,
                startTimeMs = expectedStartTimeMs,
                endTimeMs = expectedEndTimeMs,
                parent = parentSpan,
                errorCode = ErrorCode.USER_ABANDON
            )
        )

        with(verifyPublicSpan(expectedName, false, ErrorCode.USER_ABANDON)) {
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
            assertEquals(StatusCode.ERROR, status)
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
        val expectedEvents: List<EmbraceSpanEvent> =
            listOf(
                EmbraceSpanEvent(name = "event1", timestampNanos = 1_000_000L, expectedAttributes),
                EmbraceSpanEvent(name = "event2", timestampNanos = 5_000_000L, expectedAttributes),
            )

        assertTrue(
            embraceTracer.recordCompletedSpan(
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
            expectedAttributes.forEach {
                assertEquals(it.value, attributes[it.key])
            }
            assertEquals(expectedEvents, events)
        }
    }

    @Test
    fun `get same EmbraceSpan using spanId`() {
        val embraceSpan = checkNotNull(spanService.createSpan(name = "test-span"))
        assertTrue(embraceSpan.start())
        val spanId = checkNotNull(embraceSpan.spanId)
        val spanFromTracer = checkNotNull(embraceTracer.getSpan(spanId))
        assertSame(spanFromTracer, embraceSpan)
    }

    @Test
    fun `getSdkClockTimeMs is the same as the internal clock time`() {
        assertEquals(clock.now(), embraceTracer.getSdkCurrentTimeMs())
    }

    @Test
    fun `event timestamp will be converted to millis if an inappropriate value detected`() {
        spanSink.flushSpans()
        val span = checkNotNull(embraceTracer.startSpan(name = "my-span"))
        val eventTimeNanos = clock.nowInNanos()
        clock.tick(10L)
        assertTrue(span.addEvent(name = "first event", timestampMs = eventTimeNanos, attributes = null))
        assertTrue(span.addEvent(name = "second event", timestampMs = eventTimeNanos, attributes = mapOf("key" to "value")))
        assertTrue(span.stop())
        with(verifyPublicSpan("my-span")) {
            assertEquals(2, events.size)
            assertEquals(eventTimeNanos, events[0].timestampNanos)
            assertEquals(eventTimeNanos, events[1].timestampNanos)
        }
    }

    @Test
    fun `recording completed spans fallback normalizes timestamps to millis when appropriate`() {
        val expectedName = "test-span"
        val expectedStartTimeNanos = clock.nowInNanos()
        val expectedEndTimeNanos = expectedStartTimeNanos + 100L.millisToNanos()

        assertTrue(
            embraceTracer.recordCompletedSpan(
                name = expectedName,
                startTimeMs = expectedStartTimeNanos,
                endTimeMs = expectedEndTimeNanos
            )
        )

        with(verifyPublicSpan(expectedName)) {
            assertEquals(expectedStartTimeNanos, startTimeNanos)
            assertEquals(expectedEndTimeNanos, endTimeNanos)
        }
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
}
