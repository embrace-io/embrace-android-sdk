package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fixtures.TOO_LONG_SPAN_NAME
import io.embrace.android.embracesdk.internal.clock.millisToNanos
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
    private val clock = FakeClock(10000L)

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        spanSink = initModule.openTelemetryModule.spanSink
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.nowInNanos())
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
        val expectedStartTime = (clock.now() - 1L).millisToNanos()
        val expectedEndTime = (clock.now() + 2L).millisToNanos()
        assertTrue(embraceSpan.start(startTimeNanos = expectedStartTime))
        assertTrue(embraceSpan.stop(endTimeNanos = expectedEndTime))
        with(verifyPublicSpan("test-span")) {
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
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
                EmbraceSpanEvent(name = "event1", timestampNanos = 0L, expectedAttributes),
                EmbraceSpanEvent(name = "event2", timestampNanos = 5L, expectedAttributes),
            )

        val parentSpan = checkNotNull(embraceTracer.createSpan(name = "parent-span"))
        parentSpan.start()
        val returnThis = 1881L
        val expectedStartTime = clock.nowInNanos()
        val lambdaReturn = embraceTracer.recordSpan(
            name = "lambda-test-span",
            parent = parentSpan,
            attributes = expectedAttributes,
            events = expectedEvents
        ) {
            clock.tick(10)
            returnThis
        }
        val expectedEndTime = clock.nowInNanos()
        with(verifyPublicSpan(name = "lambda-test-span", traceRoot = false)) {
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
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
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L

        assertTrue(
            embraceTracer.recordCompletedSpan(
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
    fun `record basic failed span`() {
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L

        assertTrue(
            embraceTracer.recordCompletedSpan(
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
        val parentSpan = checkNotNull(embraceTracer.createSpan(name = "parent-span"))
        parentSpan.start()
        val expectedName = "child-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L

        assertTrue(
            embraceTracer.recordCompletedSpan(
                name = expectedName,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime,
                parent = parentSpan
            )
        )

        with(verifyPublicSpan(expectedName, false)) {
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
        }
    }

    @Test
    fun `record completed abandoned child span`() {
        val parentSpan = checkNotNull(embraceTracer.createSpan(name = "parent-span"))
        parentSpan.start()
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L

        assertTrue(
            embraceTracer.recordCompletedSpan(
                name = expectedName,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime,
                parent = parentSpan,
                errorCode = ErrorCode.USER_ABANDON
            )
        )

        with(verifyPublicSpan(expectedName, false, ErrorCode.USER_ABANDON)) {
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
            assertEquals(StatusCode.ERROR, status)
        }
    }

    @Test
    fun `record completed span with all the fixings`() {
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val expectedAttributes = mapOf(
            Pair("attribute1", "value1"),
            Pair("attribute2", "value2")
        )
        val expectedEvents: List<EmbraceSpanEvent> =
            listOf(
                EmbraceSpanEvent(name = "event1", timestampNanos = 0L, expectedAttributes),
                EmbraceSpanEvent(name = "event2", timestampNanos = 5L, expectedAttributes),
            )

        assertTrue(
            embraceTracer.recordCompletedSpan(
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
    fun `getSdkClockTimeNanos is the same as the internal clock time`() {
        assertEquals(clock.nowInNanos(), embraceTracer.getSdkCurrentTimeNanos())
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
