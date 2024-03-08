package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.arch.destination.SpanEventData
import io.embrace.android.embracesdk.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
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
    private lateinit var spanRepository: SpanRepository
    private lateinit var spanSink: SpanSink
    private lateinit var currentSessionSpan: CurrentSessionSpan
    private lateinit var spanService: SpanService
    private val clock = FakeClock(1000L)

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        spanSink = initModule.openTelemetryModule.spanSink
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
    }

    @Test
    fun `cannot create span before session is created`() {
        assertFalse(FakeInitModule(clock = clock).openTelemetryModule.currentSessionSpan.canStartNewSpan(null, true))
    }

    @Test
    fun `check trace limits with maximum not started traces`() {
        repeat(SpanServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(spanService.createSpan(name = "spanzzz$it", internal = false))
        }
        assertNull(spanService.createSpan(name = "failed-span", internal = false))
    }

    @Test
    fun `check trace limits with maximum traces recorded around a lambda`() {
        repeat(SpanServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertEquals("derp", spanService.recordSpan(name = "record$it", internal = false) { "derp" })
        }
        assertNull(spanService.createSpan(name = "failed-span", internal = false))
    }

    @Test
    fun `check trace limits with maximum completed traces`() {
        repeat(SpanServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertTrue(
                spanService.recordCompletedSpan(
                    name = "complete$it",
                    startTimeMs = 100L,
                    endTimeMs = 200L,
                    internal = false
                )
            )
        }
        assertNull(spanService.createSpan(name = "failed-span", internal = false))
    }

    @Test
    fun `check internal traces and child spans don't count towards limit`() {
        val parent = checkNotNull(spanService.createSpan(name = "test-span", internal = false))
        assertTrue(parent.start())
        repeat(SpanServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION - 1) {
            assertNotNull("Adding span $it failed", spanService.createSpan(name = "spanzzz$it", internal = false))
        }
        assertNull(spanService.createSpan(name = "failed-span", internal = false))
        assertNull(spanService.createSpan(name = "child-span", parent = parent, internal = false))
        assertNotNull(spanService.createSpan(name = "internal-again", internal = true))
        assertNotNull(spanService.createSpan(name = "internal-child-span", parent = parent, internal = true))
    }

    @Test
    fun `check total limit can be reached with descendant spans`() {
        var parentSpan: EmbraceSpan? = null
        repeat(SpanServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            val span = spanService.createSpan(name = "spanzzz$it", parent = parentSpan, internal = false)
            assertTrue(checkNotNull(span).start())
            parentSpan = span
        }
        assertNull(spanService.createSpan(name = "failed-span", parent = parentSpan, internal = false))
        assertFalse(
            spanService.recordCompletedSpan(
                name = "failed-span",
                startTimeMs = 100L,
                endTimeMs = 200L,
                parent = parentSpan,
                internal = false
            )
        )
        spanSink.flushSpans()
        assertEquals(2, spanService.recordSpan(name = "failed-span", parent = parentSpan, internal = false) { 2 })
        assertEquals(0, spanSink.completedSpans().size)
    }

    @Test
    fun `check internal child spans don't count towards limit`() {
        val parentSpan = checkNotNull(spanService.createSpan(name = "parent-span", internal = true))
        assertTrue(parentSpan.start())
        assertNotNull(spanService.createSpan(name = "failed-span", parent = parentSpan, internal = true))
        assertNotNull(spanService.recordSpan(name = "failed-span", parent = parentSpan, internal = true) { })
        assertTrue(
            spanService.recordCompletedSpan(
                name = "failed-span",
                startTimeMs = 100L,
                endTimeMs = 200L,
                parent = parentSpan,
                internal = true
            )
        )

        repeat(SpanServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(spanService.createSpan(name = "spanzzz$it", parent = parentSpan, internal = false))
        }
        assertNull(spanService.createSpan(name = "failed-span", parent = parentSpan, internal = false))
        assertNotNull(spanService.createSpan(name = "internal-span", parent = parentSpan, internal = true))
    }

    @Test
    fun `flushing with app termination and termination reason flushes session span with right termination type`() {
        AppTerminationCause::class.sealedSubclasses.forEach {
            val cause = checkNotNull(it.objectInstance)
            val module = FakeInitModule(clock = clock)
            val sessionSpan = module.openTelemetryModule.currentSessionSpan
            module.openTelemetryModule.spanService.initializeService(clock.now())
            val flushedSpans = sessionSpan.endSession(cause)
            assertEquals(1, flushedSpans.size)

            val lastFlushedSpan = flushedSpans[0]
            with(lastFlushedSpan) {
                assertEquals("emb-session", name)
                assertEquals(EmbType.Ux.Session.attributeValue, attributes[EmbType.Ux.Session.otelAttributeName()])
                assertEquals(StatusCode.OK, status)
                assertFalse(isKey())
                assertEquals(cause.attributeValue, attributes[cause.otelAttributeName()])
            }

            assertEquals(0, module.openTelemetryModule.spanSink.completedSpans().size)
        }
    }

    @Test
    fun `validate tracked spans update when session is ended`() {
        val embraceSpan = checkNotNull(spanService.createSpan(name = "test-span"))
        assertTrue(embraceSpan.start())
        val embraceSpanId = checkNotNull(embraceSpan.spanId)
        val parentSpan = checkNotNull(spanService.createSpan(name = "parent-span"))
        assertTrue(parentSpan.start())
        val parentSpanId = checkNotNull(parentSpan.spanId)
        val parentSpanFromService = checkNotNull(spanRepository.getSpan(parentSpanId))
        assertTrue(parentSpanFromService.stop())
        currentSessionSpan.endSession()

        // completed span not available after flush
        assertNull(spanRepository.getSpan(parentSpanId))

        // existing reference to completed span can still be used
        checkNotNull(spanService.createSpan(name = "child-span", parent = parentSpan))

        // active span from before flush is still available and working
        val activeSpanFromBeforeFlush = checkNotNull(spanRepository.getSpan(embraceSpanId))
        assertTrue(activeSpanFromBeforeFlush.stop())
        val currentSpans = spanSink.completedSpans()
        assertEquals(1, currentSpans.size)
        assertEquals("emb-test-span", currentSpans[0].name)
    }

    @Test
    fun `add event forwarded to span`() {
        currentSessionSpan.addEvent("test-event") {
            SpanEventData(SchemaType.CustomBreadcrumb(this), 1000L)
        }
        val span = currentSessionSpan.endSession(null).single()
        assertEquals("emb-session", span.name)

        // verify event was added to the span
        val testEvent = span.events.single()
        assertEquals("custom-breadcrumb", testEvent.name)
        assertEquals(1000, testEvent.timestampNanos.nanosToMillis())
        assertEquals(
            mapOf(
                EmbType.System.Breadcrumb.otelAttributeName() to EmbType.System.Breadcrumb.attributeValue,
                "message" to "test-event"
            ),
            testEvent.attributes
        )
    }

    @Test
    fun `add attribute forwarded to span`() {
        currentSessionSpan.addAttribute(SpanAttributeData("my_key", "my_value"))
        val span = currentSessionSpan.endSession(null).single()
        assertEquals("emb-session", span.name)

        // verify attribute was added to the span
        assertEquals("my_value", span.attributes["my_key"])
    }
}
