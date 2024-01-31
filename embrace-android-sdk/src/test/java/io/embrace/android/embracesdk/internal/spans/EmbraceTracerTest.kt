package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class EmbraceTracerTest {
    private lateinit var spansRepository: SpansRepository
    private lateinit var spansSink: SpansSink
    private lateinit var spansService: SpansService
    private lateinit var embraceTracer: EmbraceTracer
    private val clock = FakeClock(10000L)

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spansRepository = initModule.spansRepository
        spansSink = initModule.spansSink
        spansService = initModule.spansService
        spansService.initializeService(TimeUnit.MILLISECONDS.toNanos(clock.now()))
        embraceTracer = initModule.embraceTracer
        spansSink.flushSpans()
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
    fun `stop EmbraceSpan with different error codes`() {
        ErrorCode.values().forEach { errorCode ->
            val embraceSpan = checkNotNull(embraceTracer.createSpan(name = "test-span"))
            assertNotNull(embraceSpan)
            assertTrue(embraceSpan.start())
            assertTrue(embraceSpan.stop(errorCode))
            verifyPublicSpan("test-span")
            spansSink.flushSpans()
        }
    }

    @Test
    fun `record lambda running as span`() {
        val returnThis = 1881L
        val lambdaReturn = embraceTracer.recordSpan(name = "lambda-test-span") {
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
            embraceTracer.recordCompletedSpan(
                name = expectedName,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime
            )
        )

        with(verifyPublicSpan(expectedName)) {
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
            assertEquals("true", attributes["emb.key"])
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
        val expectedType = EmbraceAttributes.Type.PERFORMANCE
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
            assertEquals(
                expectedType.name,
                attributes[EmbraceAttributes.Type.PERFORMANCE.keyName()]
            )
            assertEquals("true", attributes["emb.key"])
            expectedAttributes.forEach {
                assertEquals(it.value, attributes[it.key])
            }
            assertEquals(expectedEvents, events)
        }
    }

    @Test
    fun `get same EmbraceSpan using spanId`() {
        val embraceSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(embraceSpan.start())
        val spanId = checkNotNull(embraceSpan.spanId)
        val spanFromTracer = checkNotNull(embraceTracer.getSpan(spanId))
        assertSame(spanFromTracer, embraceSpan)
    }

    private fun verifyPublicSpan(name: String, traceRoot: Boolean = true, errorCode: ErrorCode? = null): EmbraceSpanData {
        val currentSpans = spansSink.completedSpans()
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
