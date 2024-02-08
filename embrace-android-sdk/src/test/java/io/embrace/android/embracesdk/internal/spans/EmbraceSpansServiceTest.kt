package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceSpansServiceTest {
    private lateinit var spansSink: SpansSink
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spansService: SpansService
    private val clock = FakeClock(10000L)

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spansSink = initModule.openTelemetryModule.spansSink
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan
        spansService = initModule.openTelemetryModule.spansService
        spansService.initializeService(clock.now())
    }

    @Test
    fun `verify default behaviour before initialization`() {
        val uninitializedService = FakeInitModule(clock = clock).openTelemetryModule.spansService
        assertFalse(uninitializedService.initialized())
        assertNull(uninitializedService.createSpan("test-span"))
        assertTrue(uninitializedService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        uninitializedService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
    }

    @Test
    fun `service works once initialized`() {
        assertTrue(spansService.initialized())
        assertTrue(spansService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        spansService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
        assertEquals(2, spansSink.completedSpans().size)
        assertEquals(3, currentSessionSpan.endSession().size)
    }

    @Test
    fun `record internal completed span recording with all the fixings`() {
        spansSink.flushSpans()
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
        val currentSpans = spansSink.completedSpans()
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
        spansSink.flushSpans()
        val parent = checkNotNull(spansService.createSpan("test-span"))
        assertTrue(parent.start())
        val child = checkNotNull(spansService.createSpan(name = "test-span", parent = parent))
        assertTrue(child.start())
        assertTrue(parent.traceId == child.traceId)
        assertTrue(parent.spanId == checkNotNull(child.parent).spanId)
    }

    @Test
    fun `can record completed span after init`() {
        spansSink.flushSpans()
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

        assertEquals(1, spansSink.completedSpans().size)
    }

    @Test
    fun `can record completed child span after init`() {
        spansSink.flushSpans()
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

        val currentSpans = spansSink.completedSpans()
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)
    }

    @Test
    fun `can record span after init`() {
        spansSink.flushSpans()
        spansService.recordSpan(name = "test-span") {
            spansService.hashCode()
        }

        assertEquals(1, spansSink.completedSpans().size)
    }

    @Test
    fun `can record child span after init`() {
        spansSink.flushSpans()
        val parent = checkNotNull(spansService.createSpan("test-span"))
        assertTrue(parent.start())
        spansService.recordSpan(name = "child-span", parent = parent) {
            spansService.hashCode()
        }
        assertTrue(parent.stop())

        val currentSpans = spansSink.completedSpans()
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)
    }

    @Test
    fun `completed spans recorded before initialization will saved and recorded upon initialization`() {
        val module = FakeInitModule(clock = clock)
        val service = module.openTelemetryModule.spansService
        assertFalse(service.initialized())
        assertTrue(service.recordCompletedSpan("test-span", 10, 20))
        assertTrue(service.recordCompletedSpan("test-span", 15, 25))
        service.initializeService(clock.now())
        assertEquals(2, module.openTelemetryModule.spansSink.completedSpans().size)
    }

    @Test
    fun `verify ceiling to how many recordCompleteSpan calls can be buffered`() {
        val service = FakeInitModule(clock = clock).openTelemetryModule.spansService
        repeat(1000) {
            assertTrue(service.recordCompletedSpan("test-span", 10, 20))
        }
        assertFalse(service.recordCompletedSpan("test-span", 10, 20))
    }
}
