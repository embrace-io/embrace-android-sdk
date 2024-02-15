package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.opentelemetry.api.trace.StatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class CurrentSessionSpanImplTests {
    private lateinit var spansRepository: SpansRepository
    private lateinit var spansSink: SpansSink
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spansService: SpansService
    private val clock = FakeClock(1000L)

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spansRepository = initModule.openTelemetryModule.spansRepository
        spansSink = initModule.openTelemetryModule.spansSink
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan
        spansService = initModule.openTelemetryModule.spansService
        spansService.initializeService(clock.now())
    }

    @Test
    fun `cannot create span before session is created`() {
        assertFalse(FakeInitModule(clock = clock).openTelemetryModule.currentSessionSpan.canStartNewSpan(null, true))
    }

    @Test
    fun `check trace limits with maximum not started traces`() {
        repeat(SpansServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(spansService.createSpan(name = "spanzzz$it", internal = false))
        }
        assertNull(spansService.createSpan(name = "failed-span", internal = false))
    }

    @Test
    fun `check trace limits with maximum traces recorded around a lambda`() {
        repeat(SpansServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertEquals("derp", spansService.recordSpan(name = "record$it", internal = false) { "derp" })
        }
        assertNull(spansService.createSpan(name = "failed-span", internal = false))
    }

    @Test
    fun `check trace limits with maximum completed traces`() {
        repeat(SpansServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertTrue(
                spansService.recordCompletedSpan(
                    name = "complete$it",
                    startTimeNanos = 100L,
                    endTimeNanos = 200L,
                    internal = false
                )
            )
        }
        assertNull(spansService.createSpan(name = "failed-span", internal = false))
    }

    @Test
    fun `check internal traces and child spans don't count towards limit`() {
        val parent = checkNotNull(spansService.createSpan(name = "test-span", internal = false))
        assertTrue(parent.start())
        repeat(SpansServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION - 1) {
            assertNotNull("Adding span $it failed", spansService.createSpan(name = "spanzzz$it", internal = false))
        }
        assertNull(spansService.createSpan(name = "failed-span", internal = false))
        assertNull(spansService.createSpan(name = "child-span", parent = parent, internal = false))
        assertNotNull(spansService.createSpan(name = "internal-again", internal = true))
        assertNotNull(spansService.createSpan(name = "internal-child-span", parent = parent, internal = true))
    }

    @Test
    fun `check total limit can be reached with descendant spans`() {
        var parentSpan: EmbraceSpan? = null
        repeat(SpansServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            val span = spansService.createSpan(name = "spanzzz$it", parent = parentSpan, internal = false)
            assertTrue(checkNotNull(span).start())
            parentSpan = span
        }
        assertNull(spansService.createSpan(name = "failed-span", parent = parentSpan, internal = false))
        assertFalse(
            spansService.recordCompletedSpan(
                name = "failed-span",
                startTimeNanos = 100L,
                endTimeNanos = 200L,
                parent = parentSpan,
                internal = false
            )
        )
        spansSink.flushSpans()
        assertEquals(2, spansService.recordSpan(name = "failed-span", parent = parentSpan, internal = false) { 2 })
        assertEquals(0, spansSink.completedSpans().size)
    }

    @Test
    fun `check internal child spans don't count towards limit`() {
        val parentSpan = checkNotNull(spansService.createSpan(name = "parent-span", internal = true))
        assertTrue(parentSpan.start())
        assertNotNull(spansService.createSpan(name = "failed-span", parent = parentSpan, internal = true))
        assertNotNull(spansService.recordSpan(name = "failed-span", parent = parentSpan, internal = true) { })
        assertTrue(
            spansService.recordCompletedSpan(
                name = "failed-span",
                startTimeNanos = 100L,
                endTimeNanos = 200L,
                parent = parentSpan,
                internal = true
            )
        )

        repeat(SpansServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(spansService.createSpan(name = "spanzzz$it", parent = parentSpan, internal = false))
        }
        assertNull(spansService.createSpan(name = "failed-span", parent = parentSpan, internal = false))
        assertNotNull(spansService.createSpan(name = "internal-span", parent = parentSpan, internal = true))
    }

    @Test
    fun `flushing with app termination and termination reason flushes session span with right termination type`() {
        EmbraceAttributes.AppTerminationCause.values().forEach {
            val module = FakeInitModule(clock = clock)
            val sessionSpan = module.openTelemetryModule.currentSessionSpan
            module.openTelemetryModule.spansService.initializeService(clock.now())
            val flushedSpans = sessionSpan.endSession(it)
            assertEquals(1, flushedSpans.size)

            val lastFlushedSpan = flushedSpans[0]
            with(lastFlushedSpan) {
                assertEquals("emb-session-span", name)
                assertEquals(
                    EmbraceAttributes.Type.SESSION.name,
                    attributes[EmbraceAttributes.Type.SESSION.keyName()]
                )
                assertEquals(StatusCode.OK, status)
                assertFalse(isKey())
                assertEquals(it.name, attributes[it.keyName()])
            }

            assertEquals(0, module.openTelemetryModule.spansSink.completedSpans().size)
        }
    }

    @Test
    fun `validate tracked spans update when session is ended`() {
        val embraceSpan = checkNotNull(spansService.createSpan(name = "test-span"))
        assertTrue(embraceSpan.start())
        val embraceSpanId = checkNotNull(embraceSpan.spanId)
        val parentSpan = checkNotNull(spansService.createSpan(name = "parent-span"))
        assertTrue(parentSpan.start())
        val parentSpanId = checkNotNull(parentSpan.spanId)
        val parentSpanFromService = checkNotNull(spansRepository.getSpan(parentSpanId))
        assertTrue(parentSpanFromService.stop())
        currentSessionSpan.endSession()

        // completed span not available after flush
        assertNull(spansRepository.getSpan(parentSpanId))

        // existing reference to completed span can still be used
        checkNotNull(spansService.createSpan(name = "child-span", parent = parentSpan))

        // active span from before flush is still available and working
        val activeSpanFromBeforeFlush = checkNotNull(spansRepository.getSpan(embraceSpanId))
        assertTrue(activeSpanFromBeforeFlush.stop())
        val currentSpans = spansSink.completedSpans()
        assertEquals(1, currentSpans.size)
        assertEquals("emb-test-span", currentSpans[0].name)
    }

    @Test
    fun `add event forwarded to span`() {
        currentSessionSpan.addEvent("test-event", 1000L, mapOf("key" to "value"))
        val span = currentSessionSpan.endSession(null).single()
        assertEquals("emb-session-span", span.name)

        // verify event was added to the span
        val testEvent = span.events.single()
        assertEquals("test-event", testEvent.name)
        assertEquals(1000, testEvent.timestampNanos)
        assertEquals(mapOf("key" to "value"), testEvent.attributes)
    }

    @Test
    fun `add attribute forwarded to span`() {
        currentSessionSpan.addAttribute("my_key", "my_value")
        val span = currentSessionSpan.endSession(null).single()
        assertEquals("emb-session-span", span.name)

        // verify attribute was added to the span
        assertEquals("my_value", span.attributes["my_key"])
    }
}
