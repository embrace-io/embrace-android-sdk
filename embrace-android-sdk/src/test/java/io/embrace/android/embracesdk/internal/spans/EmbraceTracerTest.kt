package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.telemetry.EmbraceTelemetryService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceTracerTest {

    private lateinit var spansService: SpansServiceImpl
    private lateinit var embraceTracer: EmbraceTracer
    private val clock = FakeClock(10000L)

    @Before
    fun setup() {
        spansService = SpansServiceImpl(
            100L,
            200L,
            FakeOpenTelemetryClock(embraceClock = clock),
            EmbraceTelemetryService()
        )
        embraceTracer = EmbraceTracer(spansService)
        spansService.flushSpans()
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
            spansService.flushSpans()
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

    private fun verifyPublicSpan(name: String, errorCode: ErrorCode? = null): EmbraceSpanData {
        val currentSpans = spansService.completedSpans()
        assertEquals(1, currentSpans.size)
        val currentSpan = currentSpans[0]
        assertEquals(name, currentSpan.name)
        assertEquals(
            EmbraceAttributes.Type.PERFORMANCE.name,
            currentSpan.attributes[EmbraceAttributes.Type.PERFORMANCE.keyName()]
        )
        assertEquals("true", currentSpan.attributes["emb.key"])
        assertEquals(errorCode?.name, currentSpan.attributes[errorCode?.keyName()])
        assertFalse(currentSpan.isPrivate())
        return currentSpan
    }
}
