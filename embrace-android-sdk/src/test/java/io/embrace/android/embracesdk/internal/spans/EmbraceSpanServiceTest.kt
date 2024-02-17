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

internal class EmbraceSpanServiceTest {
    private lateinit var spanSink: SpanSink
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spanService: SpanService
    private val clock = FakeClock(10000L)

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spanSink = initModule.openTelemetryModule.spanSink
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
    }

    @Test
    fun `verify default behaviour before initialization`() {
        val uninitializedService = FakeInitModule(clock = clock).openTelemetryModule.spanService
        assertFalse(uninitializedService.initialized())
        assertNull(uninitializedService.createSpan("test-span"))
        assertNull(uninitializedService.startSpan("test-span"))
        assertTrue(uninitializedService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        uninitializedService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
    }

    @Test
    fun `service works once initialized`() {
        assertTrue(spanService.initialized())
        assertTrue(spanService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        spanService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
        assertEquals(2, spanSink.completedSpans().size)
        assertEquals(3, currentSessionSpan.endSession().size)
    }

    @Test
    fun `record internal completed span recording with all the fixings`() {
        spanSink.flushSpans()
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

        spanService.recordCompletedSpan(
            name = expectedName,
            startTimeNanos = expectedStartTime,
            endTimeNanos = expectedEndTime,
            type = expectedType,
            attributes = expectedAttributes,
            events = expectedEvents
        )

        val name = "emb-$expectedName"
        val currentSpans = spanSink.completedSpans()
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
        spanSink.flushSpans()
        val parent = checkNotNull(spanService.createSpan("test-span"))
        assertTrue(parent.start())
        val child = checkNotNull(spanService.createSpan(name = "test-span", parent = parent))
        assertTrue(child.start())
        assertTrue(parent.traceId == child.traceId)
        assertTrue(parent.spanId == checkNotNull(child.parent).spanId)
    }

    @Test
    fun `can record completed span after init`() {
        spanSink.flushSpans()
        val expectedName = "test-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        assertTrue(
            spanService.recordCompletedSpan(
                name = expectedName,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime
            )
        )

        assertEquals(1, spanSink.completedSpans().size)
    }

    @Test
    fun `can record completed child span after init`() {
        spanSink.flushSpans()
        val expectedName = "child-span"
        val expectedStartTime = clock.now()
        val expectedEndTime = expectedStartTime + 100L
        val parentSpan = checkNotNull(spanService.createSpan(name = "test-span"))
        assertTrue(parentSpan.start())
        assertTrue(
            spanService.recordCompletedSpan(
                name = expectedName,
                parent = parentSpan,
                startTimeNanos = expectedStartTime,
                endTimeNanos = expectedEndTime
            )
        )
        assertTrue(parentSpan.stop())

        val currentSpans = spanSink.completedSpans()
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)
    }

    @Test
    fun `can record span after init`() {
        spanSink.flushSpans()
        spanService.recordSpan(name = "test-span") {
            spanService.hashCode()
        }

        assertEquals(1, spanSink.completedSpans().size)
    }

    @Test
    fun `can record child span after init`() {
        spanSink.flushSpans()
        val parent = checkNotNull(spanService.createSpan("test-span"))
        assertTrue(parent.start())
        spanService.recordSpan(name = "child-span", parent = parent) {
            spanService.hashCode()
        }
        assertTrue(parent.stop())

        val currentSpans = spanSink.completedSpans()
        assertEquals(2, currentSpans.size)
        assertTrue(currentSpans[0].traceId == currentSpans[1].traceId)
        assertTrue(currentSpans[0].parentSpanId == currentSpans[1].spanId)
    }

    @Test
    fun `completed spans recorded before initialization will saved and recorded upon initialization`() {
        val module = FakeInitModule(clock = clock)
        val service = module.openTelemetryModule.spanService
        assertFalse(service.initialized())
        assertTrue(service.recordCompletedSpan("test-span", 10, 20))
        assertTrue(service.recordCompletedSpan("test-span", 15, 25))
        service.initializeService(clock.now())
        assertEquals(2, module.openTelemetryModule.spanSink.completedSpans().size)
    }

    @Test
    fun `verify ceiling to how many recordCompleteSpan calls can be buffered`() {
        val service = FakeInitModule(clock = clock).openTelemetryModule.spanService
        repeat(1000) {
            assertTrue(service.recordCompletedSpan("test-span", 10, 20))
        }
        assertFalse(service.recordCompletedSpan("test-span", 10, 20))
    }
}
