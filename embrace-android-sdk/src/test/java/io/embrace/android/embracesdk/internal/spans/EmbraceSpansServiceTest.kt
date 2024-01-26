package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceSpansServiceTest {

    private lateinit var spansService: EmbraceSpansService
    private val clock = FakeClock(10000L)

    @Before
    fun setup() {
        spansService = EmbraceSpansService(
            clock = OpenTelemetryClock(clock),
            telemetryService = FakeTelemetryService()
        )
    }

    @Test
    fun `verify default behaviour before initialization`() {
        assertFalse(spansService.initialized())
        assertNull(spansService.createSpan("test-span"))
        assertTrue(spansService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        spansService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
        assertNull(spansService.completedSpans())
        assertNull(spansService.flushSpans())
        assertEquals(CompletableResultCode.ofFailure(), spansService.storeCompletedSpans(listOf()))
        assertNull(spansService.getSpan("some-span-id"))
    }

    @Test
    fun `service works once initialized`() {
        initializeService()
        assertTrue(spansService.initialized())
        assertTrue(spansService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        spansService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
        assertEquals(2, spansService.completedSpans()?.size)
        assertEquals(3, spansService.flushSpans()?.size)
    }

    @Test
    fun `record internal completed span recording with all the fixings`() {
        initializeService()
        spansService.flushSpans()
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val expectedType = EmbraceAttributes.Type.PERFORMANCE
        val expectedAttributes = mapOf(
            Pair("attribute1", "value1"),
            Pair("attribute2", "value2")
        )
        val expectedEvents = listOf(
            EmbraceSpanEvent(name = "event1", timestampNanos = 0L, attributes = expectedAttributes),
            EmbraceSpanEvent(name = "event2", timestampNanos = 5L, attributes = expectedAttributes)
        )

        spansService.recordCompletedSpan(
            name = expectedName,
            startTimeNanos = expectedStartTime,
            endTimeNanos = expectedEndTime,
            type = expectedType,
            attributes = expectedAttributes,
            events = expectedEvents
        )

        val name = "emb-$expectedName"
        val currentSpans = checkNotNull(spansService.completedSpans())
        assertEquals(1, currentSpans.size)
        val span = currentSpans[0]

        with(span) {
            assertEquals(name, name)
            assertEquals(expectedStartTime, startTimeNanos)
            assertEquals(expectedEndTime, endTimeNanos)
            assertEquals(expectedType.name, attributes[EmbraceAttributes.Type.PERFORMANCE.keyName()])
            expectedAttributes.forEach {
                assertEquals(it.value, attributes[it.key])
            }
            assertEquals(expectedEvents, events)
        }
    }

    @Test
    fun `can create spans after init`() {
        initializeService()
        spansService.flushSpans()
        val parent = checkNotNull(spansService.createSpan("test-span"))
        assertTrue(parent.start())
        val child = checkNotNull(spansService.createSpan(name = "test-span", parent = parent))
        assertTrue(child.start())
        assertTrue(parent.traceId == child.traceId)
        assertTrue(parent.spanId == checkNotNull(child.parent).spanId)
    }

    @Test
    fun `can record completed span after init`() {
        initializeService()
        spansService.flushSpans()
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        assertTrue(
            spansService.recordCompletedSpan(
                name = expectedName,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime
            )
        )

        assertEquals(1, checkNotNull(spansService.completedSpans()).size)
    }

    @Test
    fun `can record completed child span after init`() {
        initializeService()
        spansService.flushSpans()
        val expectedName = "child-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val parentSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        assertTrue(
            spansService.recordCompletedSpan(
                name = expectedName,
                parent = parentSpan,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime
            )
        )
        assertTrue(parentSpan.stop())

        val currentSpans = checkNotNull(spansService.completedSpans())
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)
    }

    @Test
    fun `can record span after init`() {
        initializeService()
        spansService.flushSpans()
        spansService.recordSpan(name = "test-span") {
            spansService.hashCode()
        }

        assertEquals(1, checkNotNull(spansService.completedSpans()).size)
    }

    @Test
    fun `can record child span after init`() {
        initializeService()
        spansService.flushSpans()
        val parent = checkNotNull(spansService.createSpan("test-span"))
        assertTrue(parent.start())
        spansService.recordSpan(name = "child-span", parent = parent) {
            spansService.hashCode()
        }
        assertTrue(parent.stop())

        val currentSpans = checkNotNull(spansService.completedSpans())
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)
    }

    @Test
    fun `completed spans recorded before initialization will saved and recorded upon initialization`() {
        assertFalse(spansService.initialized())
        assertTrue(spansService.recordCompletedSpan("test-span", 10, 20))
        assertTrue(spansService.recordCompletedSpan("test-span", 15, 25))
        initializeService()
        assertEquals(2, spansService.completedSpans()?.size)
    }

    @Test
    fun `verify ceiling to how many recordCompleteSpan calls can be buffered`() {
        repeat(1000) {
            assertTrue(spansService.recordCompletedSpan("test-span", 10, 20))
        }
        assertFalse(spansService.recordCompletedSpan("test-span", 10, 20))
    }

    @Test
    fun `can get span with spanId`() {
        assertNull(spansService.getSpan("blah"))
        initializeService()
        val span = checkNotNull(spansService.createSpan("test-span"))
        assertTrue(span.start())
        val spanId = checkNotNull(span.spanId)
        val spanFromId = spansService.getSpan(spanId)
        assertSame(spanFromId, span)
    }

    private fun initializeService() {
        spansService.initializeService(1)
    }
}
