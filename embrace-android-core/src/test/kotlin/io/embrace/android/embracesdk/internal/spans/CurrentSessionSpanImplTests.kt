package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.assertions.assertEmbraceSpanData
import io.embrace.android.embracesdk.assertions.assertError
import io.embrace.android.embracesdk.assertions.assertHasEmbraceAttribute
import io.embrace.android.embracesdk.assertions.assertIsType
import io.embrace.android.embracesdk.assertions.validatePreviousSessionLink
import io.embrace.android.embracesdk.assertions.validateSystemLink
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSpanFactory
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.config.instrumented.schema.OtelLimitsConfig
import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.spans.NoopEmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl.Companion.MAX_INTERNAL_SPANS_PER_SESSION
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpanImpl.Companion.MAX_NON_INTERNAL_SPANS_PER_SESSION
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class, IncubatingApi::class)
internal class CurrentSessionSpanImplTests {

    private lateinit var spanRepository: SpanRepository
    private lateinit var spanSink: SpanSink
    private lateinit var otelLimitsConfig: OtelLimitsConfig
    private lateinit var telemetryService: TelemetryService
    private lateinit var openTelemetryClock: Clock
    private lateinit var currentSessionSpan: CurrentSessionSpanImpl
    private lateinit var spanService: SpanService
    private lateinit var tracer: Tracer
    private lateinit var openTelemetry: OpenTelemetry
    private val clock = FakeClock(1000L)

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock = clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        spanSink = initModule.openTelemetryModule.spanSink
        telemetryService = initModule.telemetryService
        openTelemetryClock = initModule.openTelemetryModule.openTelemetryClock
        currentSessionSpan = initModule.openTelemetryModule.currentSessionSpan as CurrentSessionSpanImpl
        tracer = initModule.openTelemetryModule.otelSdkWrapper.sdkTracer
        openTelemetry = initModule.openTelemetryModule.otelSdkWrapper.openTelemetryKotlin
        spanService = initModule.openTelemetryModule.spanService
        spanService.initializeService(clock.now())
        otelLimitsConfig = initModule.instrumentedConfig.otelLimits
    }

    @Test
    fun `session span ready when initialized`() {
        assertTrue(currentSessionSpan.initialized())
        currentSessionSpan.assertSessionSpan()
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
    fun `cannot create child if parent not started`() {
        assertFalse(currentSessionSpan.canStartNewSpan(FakeEmbraceSdkSpan(), false))
    }

    @Test
    fun `can create child if parent has stopped`() {
        assertTrue(currentSessionSpan.canStartNewSpan(FakeEmbraceSdkSpan.stopped(), false))
    }

    @Test
    fun `after ending session with app termination, spans cannot be recorded`() {
        currentSessionSpan.endSession(true, AppTerminationCause.UserTermination)
        assertFalse(currentSessionSpan.canStartNewSpan(null, true))
    }

    @Test
    fun `check trace limits with maximum not started traces`() {
        repeat(MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(
                spanService.createSpan(
                    name = "spanzzz$it",
                    internal = false
                )
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                internal = false
            )
        )
    }

    @Test
    fun `check trace limits with maximum internal not started traces`() {
        repeat(MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(
                spanService.createSpan(
                    name = "spanzzz$it",
                    internal = false
                )
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                internal = false
            )
        )

        repeat(MAX_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(spanService.createSpan(name = "internal$it"))
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(name = "failed-span")
        )
    }

    @Test
    fun `check trace limited applied to spans created with span builder`() {
        repeat(MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(
                spanService.createSpan(
                    otelSpanStartArgs = OtelSpanStartArgs(
                        name = "external-span",
                        type = EmbType.Performance.Default,
                        internal = false,
                        private = false,
                        tracer = tracer,
                        openTelemetry = openTelemetry,
                    )
                )
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                otelSpanStartArgs = OtelSpanStartArgs(
                    name = "external-span",
                    type = EmbType.Performance.Default,
                    internal = false,
                    private = false,
                    tracer = tracer,
                    openTelemetry = openTelemetry,
                )
            )
        )
        assertNotNull(
            spanService.createSpan(
                otelSpanStartArgs = OtelSpanStartArgs(
                    name = "internal-span",
                    type = EmbType.Performance.Default,
                    internal = true,
                    private = false,
                    tracer = tracer,
                    openTelemetry = openTelemetry,
                )
            )
        )
    }

    @Test
    fun `check trace limits with maximum traces recorded around a lambda`() {
        repeat(MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertEquals(
                "derp",
                spanService.recordSpan(
                    name = "record$it",
                    internal = false,
                ) { "derp" }
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                internal = false,
            )
        )
    }

    @Test
    fun `check trace limits with maximum completed traces`() {
        repeat(MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertTrue(
                spanService.recordCompletedSpan(
                    name = "complete$it",
                    startTimeMs = 100L,
                    endTimeMs = 200L,
                    internal = false,
                )
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                internal = false,
            )
        )
    }

    @Test
    fun `check internal traces and child spans don't count towards limit`() {
        val parent = checkNotNull(
            spanService.createSpan(
                name = "test-span",
                internal = false,
            )
        )
        assertTrue(parent.start())
        repeat(MAX_NON_INTERNAL_SPANS_PER_SESSION - 1) {
            assertNotNull(
                "Adding span $it failed",
                spanService.createSpan(
                    name = "spanzzz$it",
                    internal = false,
                )
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                internal = false,
            )
        )
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "child-span",
                parent = parent,
                internal = false,
            )
        )
        assertNotNull(
            spanService.createSpan(
                name = "internal-again",
            )
        )
        assertNotNull(
            spanService.createSpan(
                name = "internal-child-span",
                parent = parent,
            )
        )
    }

    @Test
    fun `check total limit can be reached with descendant spans`() {
        var parentSpan: EmbraceSpan? = null
        repeat(MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            val span = spanService.createSpan(
                name = "spanzzz$it",
                parent = parentSpan,
                internal = false,
            )
            assertTrue(checkNotNull(span).start())
            parentSpan = span
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                parent = parentSpan,
                internal = false,
            )
        )
        assertFalse(
            spanService.recordCompletedSpan(
                name = "failed-span",
                startTimeMs = 100L,
                endTimeMs = 200L,
                parent = parentSpan,
                internal = false,
            )
        )
        spanSink.flushSpans()
        assertEquals(
            2,
            spanService.recordSpan(
                name = "failed-span",
                parent = parentSpan,
                internal = false,
            ) { 2 }
        )
        assertEquals(0, spanSink.completedSpans().size)
    }

    @Test
    fun `check internal child spans don't count towards limit`() {
        val parentSpan = checkNotNull(
            spanService.createSpan(
                name = "parent-span",
            )
        )
        assertTrue(parentSpan.start())
        assertNotNull(
            spanService.createSpan(
                name = "failed-span",
                parent = parentSpan,
            )
        )
        assertNotNull(
            spanService.recordSpan(
                name = "failed-span",
                parent = parentSpan,
            ) { }
        )
        assertTrue(
            spanService.recordCompletedSpan(
                name = "failed-span",
                startTimeMs = 100L,
                endTimeMs = 200L,
                parent = parentSpan,
            )
        )

        repeat(MAX_NON_INTERNAL_SPANS_PER_SESSION) {
            assertNotNull(
                spanService.createSpan(
                    name = "spanzzz$it",
                    parent = parentSpan,
                    internal = false,
                )
            )
        }
        assertEquals(
            NoopEmbraceSdkSpan,
            spanService.createSpan(
                name = "failed-span",
                parent = parentSpan,
                internal = false,
            )
        )
        assertNotNull(
            spanService.createSpan(
                name = "internal-span",
                parent = parentSpan,
            )
        )
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
            span = flushedSpans["emb-session"]?.toEmbracePayload(),
            expectedStartTimeMs = sessionStartTimeMs,
            expectedEndTimeMs = crashTimeMs,
            expectedParentId = OtelIds.INVALID_SPAN_ID,
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                AppTerminationCause.Crash.asPair(),
                EmbType.Ux.Session.asPair()
            )
        )

        assertEmbraceSpanData(
            span = flushedSpans[crashedSpanName]?.toEmbracePayload(),
            expectedStartTimeMs = crashSpanStartTimeMs,
            expectedEndTimeMs = crashTimeMs,
            expectedParentId = OtelIds.INVALID_SPAN_ID,
            expectedErrorCode = ErrorCode.FAILURE,
            expectedCustomAttributes = mapOf(
                EmbType.Performance.Default.asPair()
            )
        )

        assertEquals(0, spanSink.completedSpans().size)
        assertEquals(0, spanRepository.getActiveSpans().size)
    }

    @Test
    fun `new session started after ending has correct metadata`() {
        val originalSessionSpan = checkNotNull(spanRepository.getActiveSpans().single().snapshot())
        val originalSessionId = currentSessionSpan.getSessionId()
        currentSessionSpan.endSession(startNewSession = true)
        with(spanRepository.getActiveSpans().single()) {
            assertTrue(hasEmbraceAttribute(EmbType.Ux.Session))
            assertNotEquals(originalSessionSpan.spanId, spanId)
            checkNotNull(snapshot()?.links?.single()).validatePreviousSessionLink(originalSessionSpan, originalSessionId)
        }
    }

    @Test
    fun `new session will only start if told to`() {
        assertNotNull(spanRepository.getActiveSpans().single())
        currentSessionSpan.endSession(startNewSession = false)
        assertTrue(spanRepository.getActiveSpans().isEmpty())
    }

    @Test
    fun `calling readySession creates a session span if not present`() {
        currentSessionSpan.endSession(startNewSession = false)
        currentSessionSpan.assertNoSessionSpan()
        assertTrue(currentSessionSpan.readySession())
        currentSessionSpan.assertSessionSpan()
    }

    @Test
    fun `readySession will not replace existing session span`() {
        val originalSessionSpanId = spanRepository.getActiveSpans().single().spanId
        assertTrue(currentSessionSpan.readySession())
        assertEquals(originalSessionSpanId, spanRepository.getActiveSpans().single().spanId)
    }

    @Test
    fun `readySession will return false if session span is not recording`() {
        val sessionSpan = CurrentSessionSpanImpl(
            openTelemetryClock = openTelemetryClock,
            telemetryService = telemetryService,
            spanRepository = spanRepository,
            spanSink = spanSink,
            embraceSpanFactorySupplier = { FakeEmbraceSpanFactory() },
            tracerSupplier = { FakeTracer() },
            openTelemetrySupplier = ::openTelemetry
        )
        assertFalse(sessionSpan.readySession())
    }

    @Test
    fun `validate tracked spans update when session is ended`() {
        val embraceSpan =
            checkNotNull(spanService.createSpan(name = "test-span"))
        assertTrue(embraceSpan.start())
        val embraceSpanId = checkNotNull(embraceSpan.spanId)
        val parentSpan =
            checkNotNull(spanService.createSpan(name = "parent-span"))
        assertTrue(parentSpan.start())
        val parentSpanId = checkNotNull(parentSpan.spanId)
        val parentSpanFromService = checkNotNull(spanRepository.getSpan(parentSpanId))
        assertTrue(parentSpanFromService.stop())
        currentSessionSpan.endSession(startNewSession = true)

        // completed span not available after flush
        assertNull(spanRepository.getSpan(parentSpanId))

        // existing reference to completed span can still be used
        checkNotNull(
            spanService.createSpan(
                name = "child-span",
                parent = parentSpan,
            )
        )

        // active span from before flush is still available and working
        val activeSpanFromBeforeFlush = checkNotNull(spanRepository.getSpan(embraceSpanId))
        assertTrue(activeSpanFromBeforeFlush.stop())
        val currentSpans = spanSink.completedSpans()
        assertEquals(1, currentSpans.size)
        assertEquals("emb-test-span", currentSpans[0].name)
    }

    @Test
    fun `span stop callback creates the correct span links`() {
        val sessionSpan = checkNotNull(spanRepository.getActiveSpans().single())
        val sessionId = checkNotNull(sessionSpan.getSystemAttribute(SessionAttributes.SESSION_ID))
        val span = spanService.startSpan("test").apply {
            stop()
        }

        val spanSnapshot = checkNotNull(span.snapshot())
        val sessionSpanSnapshot = checkNotNull(sessionSpan.snapshot())

        checkNotNull(spanSnapshot.links).single().validateSystemLink(
            linkedSpan = sessionSpanSnapshot,
            type = LinkType.EndSession,
            expectedAttributes = mapOf(SessionAttributes.SESSION_ID to sessionId)
        )
        checkNotNull(sessionSpanSnapshot.links).single().validateSystemLink(spanSnapshot, LinkType.EndedIn)
    }

    @Test
    fun `session ending will not create span link to its own session span`() {
        val sessionSpan = checkNotNull(spanRepository.getActiveSpans().single())
        currentSessionSpan.endSession(startNewSession = true)
        val sessionSpanSnapshot = checkNotNull(sessionSpan.snapshot())
        assertEquals(0, sessionSpanSnapshot.links?.size)
    }

    @Test
    fun `span stop callback will not create links for untracked span`() {
        val sessionSpan = checkNotNull(spanRepository.getActiveSpans().single())
        currentSessionSpan.spanStopCallback(checkNotNull(FakeEmbraceSdkSpan.started().spanId))

        val sessionSpanSnapshot = checkNotNull(sessionSpan.snapshot())
        assertEquals(0, sessionSpanSnapshot.links?.size)
    }

    @Test
    fun `span stop callback will not create links if there's no active session`() {
        val span = spanService.startSpan("test").apply {
            currentSessionSpan.endSession(false)
            stop()
        }

        val spanSnapshot = checkNotNull(span.snapshot())
        assertEquals(0, spanSnapshot.links?.size)
    }

    private fun CurrentSessionSpan.assertNoSessionSpan() {
        assertEquals("", getSessionId())
        assertFalse(canStartNewSpan(parent = null, internal = true))
        assertTrue(endSession(true).isEmpty())
    }

    private fun CurrentSessionSpan.assertSessionSpan() {
        assertTrue(getSessionId().isNotBlank())
        assertTrue(canStartNewSpan(parent = null, internal = true))
    }
}
