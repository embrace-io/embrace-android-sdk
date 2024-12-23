package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.assertIsTypePerformance
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        assertNotNull(spanService.createSpan("test-span"))
        assertNotNull(spanService.startSpan("test-span"))
        assertTrue(spanService.recordCompletedSpan("test-span", 10, 20))
        var lambdaRan = false
        spanService.recordSpan("test-span") { lambdaRan = true }
        assertTrue(lambdaRan)
        assertEquals(2, spanSink.completedSpans().size)
        assertEquals(3, currentSessionSpan.endSession(startNewSession = true).size)
    }

    @Test
    fun `record internal completed span recording with all the fixings`() {
        spanSink.flushSpans()
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L
        val expectedType = EmbType.Performance.Default
        val expectedAttributes = mapOf(
            Pair("attribute1", "value1"),
            Pair("attribute2", "value2")
        )
        val expectedEvents = listOfNotNull(
            EmbraceSpanEvent.create(name = "event1", timestampMs = 1_000_000L.nanosToMillis(), expectedAttributes),
            EmbraceSpanEvent.create(name = "event2", timestampMs = 5_000_000L.nanosToMillis(), expectedAttributes),
        )

        spanService.recordCompletedSpan(
            name = expectedName,
            startTimeMs = expectedStartTimeMs,
            endTimeMs = expectedEndTimeMs,
            type = expectedType,
            attributes = expectedAttributes,
            events = expectedEvents,
        )

        val name = "emb-$expectedName"
        val currentSpans = spanSink.completedSpans()
        assertEquals(1, currentSpans.size)
        val span = currentSpans[0]

        with(span) {
            assertEquals(name, name)
            assertEquals(expectedStartTimeMs, startTimeNanos.nanosToMillis())
            assertEquals(expectedEndTimeMs, endTimeNanos.nanosToMillis())
            assertIsTypePerformance()
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
        val child =
            checkNotNull(spanService.createSpan(name = "child-span", parent = parent))
        assertTrue(child.start(startTimeMs = clock.now() - 10))
        val grandchild =
            checkNotNull(
                spanService.startSpan(
                    name = "grand-child-span",
                    parent = child,
                    startTimeMs = clock.now() + 1L,
                )
            )
        assertTrue(grandchild.stop())
        assertTrue(child.stop())
        assertTrue(parent.stop(endTimeMs = clock.now() + 50))
        assertTrue(parent.traceId == child.traceId)
        assertTrue(parent.spanId == checkNotNull(child.parent).spanId)
    }

    @Test
    fun `can record completed span after init`() {
        spanSink.flushSpans()
        val expectedName = "test-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L
        assertTrue(
            spanService.recordCompletedSpan(
                name = expectedName,
                startTimeMs = expectedStartTimeMs,
                endTimeMs = expectedEndTimeMs,
            )
        )

        assertEquals(1, spanSink.completedSpans().size)
    }

    @Test
    fun `can record completed child span after init`() {
        spanSink.flushSpans()
        val expectedName = "child-span"
        val expectedStartTimeMs = clock.now()
        val expectedEndTimeMs = expectedStartTimeMs + 100L
        val parentSpan = checkNotNull(spanService.createSpan("test-span"))
        assertTrue(parentSpan.start())
        assertTrue(
            spanService.recordCompletedSpan(
                name = expectedName,
                startTimeMs = expectedStartTimeMs,
                endTimeMs = expectedEndTimeMs,
                parent = parentSpan,
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
        spanService.recordSpan("test-span") {
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
