package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.assertError
import io.embrace.android.embracesdk.arch.assertHasEmbraceAttribute
import io.embrace.android.embracesdk.arch.assertIsType
import io.embrace.android.embracesdk.arch.assertNotKeySpan
import io.embrace.android.embracesdk.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.findEventOfType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.Tracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class CurrentSessionSpanImplTests {
    private lateinit var spanRepository: SpanRepository
    private lateinit var spanSink: SpanSink
    private lateinit var currentSessionSpan: CurrentSessionSpanImpl
    private lateinit var spanService: SpanService
    private lateinit var tracer: Tracer
    private val clock = FakeClock(1000L)

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        spanSink = initModule.openTelemetryModule.spanSink
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan as CurrentSessionSpanImpl
        tracer = initModule.openTelemetryModule.sdkTracer
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
    }

    @Test
    fun `cannot create span before session is created`() {
        val uninitialized = FakeInitModule(clock = clock).openTelemetryModule.currentSessionSpan
        assertFalse(uninitialized.initialized())
        uninitialized.assertNoSessionSpan()
    }

    @Test
    fun `cannot create spans or add data to current span if no current span exists`() {
        currentSessionSpan.endSession(startNewSession = false)
        assertTrue(currentSessionSpan.initialized())
        currentSessionSpan.assertNoSessionSpan()
    }

    @Test
    fun `check trace limits with maximum not started traces`() {
        repeat(SpanServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(spanService.createSpan(name = "spanzzz$it", internal = false))
        }
        assertNull(spanService.createSpan(name = "failed-span", internal = false))
    }

    @Test
    fun `check trace limits with maximum internal not started traces`() {
        repeat(SpanServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(spanService.createSpan(name = "spanzzz$it", internal = false))
        }
        assertNull(spanService.createSpan(name = "failed-span", internal = false))

        repeat(SpanServiceImpl.MAX_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(spanService.createSpan(name = "internal$it"))
        }
        assertNull(spanService.createSpan(name = "failed-span"))
    }

    @Test
    fun `check trace limited applied to spans created with span builder`() {
        repeat(SpanServiceImpl.MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(
                spanService.createSpan(
                    embraceSpanBuilder = tracer.embraceSpanBuilder(
                        name = "external-span",
                        type = EmbType.Performance.Default,
                        parent = null,
                        internal = false,
                        private = false,
                    )
                )
            )
        }
        assertNull(
            spanService.createSpan(
                embraceSpanBuilder = tracer.embraceSpanBuilder(
                    name = "external-span",
                    type = EmbType.Performance.Default,
                    parent = null,
                    internal = false,
                    private = false,
                )
            )
        )
        assertNotNull(
            spanService.createSpan(
                embraceSpanBuilder = tracer.embraceSpanBuilder(
                    name = "internal-span",
                    type = EmbType.Performance.Default,
                    parent = null,
                    internal = true,
                    private = false,
                )
            )
        )
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
            val flushedSpans = sessionSpan.endSession(true, cause)
            assertEquals(1, flushedSpans.size)

            val lastFlushedSpan = flushedSpans[0]
            with(lastFlushedSpan) {
                assertEquals("emb-session", name)
                assertIsType(EmbType.Ux.Session)
                assertError(ErrorCode.FAILURE)
                assertNotKeySpan()
                assertHasEmbraceAttribute(cause)
            }

            assertEquals(0, module.openTelemetryModule.spanSink.completedSpans().size)
        }
    }

    @Test
    fun `crashing results in the session span and active spans being terminated`() {
        val sessionStartTimeMs = clock.now()
        clock.tick(100)

        val crashedSpanName = "crashed-span"
        spanService.startSpan(name = crashedSpanName, internal = false)

        val crashSpanStartTimeMs = clock.now()
        clock.tick(500)

        val crashTimeMs = clock.now()
        val flushedSpans = currentSessionSpan.endSession(true, AppTerminationCause.Crash).associateBy { it.name }

        assertEmbraceSpanData(
            span = flushedSpans["emb-session"]?.toNewPayload(),
            expectedStartTimeMs = sessionStartTimeMs,
            expectedEndTimeMs = crashTimeMs,
            expectedParentId = SpanId.getInvalid(),
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                AppTerminationCause.Crash.toEmbraceKeyValuePair(),
                EmbType.Ux.Session.toEmbraceKeyValuePair()
            ),
            private = false
        )

        assertEmbraceSpanData(
            span = flushedSpans[crashedSpanName]?.toNewPayload(),
            expectedStartTimeMs = crashSpanStartTimeMs,
            expectedEndTimeMs = crashTimeMs,
            expectedParentId = SpanId.getInvalid(),
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                EmbType.Performance.Default.toEmbraceKeyValuePair()
            ),
            key = true
        )

        assertEquals(0, spanSink.completedSpans().size)
        assertEquals(0, spanRepository.getActiveSpans().size)
    }

    @Test
    fun `ending a session will only start a new session span if told to`() {
        val originalSessionSpanId = spanRepository.getActiveSpans().single().spanId
        currentSessionSpan.endSession(startNewSession = true)
        with(spanRepository.getActiveSpans().single()) {
            assertTrue(hasFixedAttribute(EmbType.Ux.Session))
            assertNotEquals(originalSessionSpanId, spanId)
        }

        currentSessionSpan.endSession(startNewSession = false)
        assertTrue(spanRepository.getActiveSpans().isEmpty())
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
        currentSessionSpan.endSession(startNewSession = true)

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
        currentSessionSpan.addEvent(SchemaType.Breadcrumb("test-event"), 1000L)
        val span = currentSessionSpan.endSession(true).single()
        assertEquals("emb-session", span.name)

        // verify event was added to the span
        val testEvent = span.toNewPayload().findEventOfType(EmbType.System.Breadcrumb)
        assertEquals(1000L, testEvent.timestampNanos?.nanosToMillis())

        val attrs = checkNotNull(testEvent.attributes)
        assertEquals("test-event", attrs.single { it.key == "message" }.data)
        assertEquals("sys.breadcrumb", attrs.single { it.key == "emb.type" }.data)
    }

    @Test
    fun `add and remove attribute forwarded to span`() {
        currentSessionSpan.addCustomAttribute(SpanAttributeData("my_key", "my_value"))
        currentSessionSpan.addCustomAttribute(SpanAttributeData("missing", "my_value"))
        currentSessionSpan.removeCustomAttribute("missing")
        val span = currentSessionSpan.endSession(true).single()
        assertEquals("emb-session", span.name)

        // verify attribute was added to the span if it wasn't removed
        assertEquals("my_value", span.attributes["my_key"])
        assertNull(span.attributes["missing"])
    }

    private fun CurrentSessionSpan.assertNoSessionSpan() {
        assertEquals("", getSessionId())
        assertFalse(canStartNewSpan(parent = null, internal = true))
        assertTrue(endSession(true).isEmpty())
        assertFalse(addCustomAttribute(attribute = SpanAttributeData("test", "test")))
        assertFalse(removeCustomAttribute("test"))
        assertFalse(addEvent(SchemaType.Breadcrumb("test"), clock.now()))
        // check doesn't throw exception
        removeEvents(EmbType.System.Breadcrumb)
    }
}
