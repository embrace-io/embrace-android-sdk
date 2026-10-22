package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.createContext
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.tracing.SpanKind
import io.opentelemetry.kotlin.tracing.StatusData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(Parameterized::class)
internal class EmbTracerTest(
    private val otelSdkMode: OtelSdkMode,
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<Array<Any>> = OtelSdkMode.parameters()
    }
    private val clock = FakeClock()
    private val openTelemetryClock = FakeOtelKotlinClock(clock)
    private val openTelemetry = fakeOpenTelemetry(otelSdkMode)

    private lateinit var spanService: FakeSpanService
    private lateinit var sdkTracer: FakeTracer
    private lateinit var tracer: EmbTracer

    @Before
    fun setup() {
        spanService = FakeSpanService()
        sdkTracer = FakeTracer()
        tracer = EmbTracer(
            impl = sdkTracer,
            spanService = spanService,
            clock = openTelemetryClock,
            openTelemetry = openTelemetry,
            errorHandler = FakeErrorHandler(),
        )
    }

    @Test
    fun `check span generated with default parameters`() {
        tracer.startSpan("foo").end()
        val fakeCreatedSpan = spanService.createdSpans.single()
        with(fakeCreatedSpan) {
            assertNull(parent)
            assertEquals("foo", name)
            assertEquals(EmbType.Performance.Default, type)
        }
    }

    @Test
    fun `check span generated with non default parameters`() {
        val parentCtx = NoopOpenTelemetry.context.root()
        tracer.startSpan(
            "foo",
            parentContext = parentCtx,
            spanKind = SpanKind.CLIENT,
            startTimestamp = 500L.nanosToMillis(),
        ) {
            setStringAttribute("foo", "bar")
        }
        val fakeCreatedSpan = spanService.createdSpans.single()
        with(fakeCreatedSpan) {
            assertEquals(parentCtx, parentContext)
            assertEquals("foo", name)
            assertEquals(SpanKind.CLIENT, spanKind)
            assertEquals("bar", attributes["foo"])
        }
    }

    @Test
    fun `span with no explicit parent inherits the span attached to the current thread`() {
        val parent = FakeEmbraceSdkSpan(openTelemetry = openTelemetry)
        val scope = parent.createContext(openTelemetry).attach()
        try {
            tracer.startSpan("foo").end()
        } finally {
            scope.detach()
        }
        assertSame(parent, spanService.createdSpans.single().parent)
    }

    @Test
    fun `span with no explicit parent has no parent when nothing is attached to the current thread`() {
        val parent = FakeEmbraceSdkSpan(openTelemetry = openTelemetry)
        val scope = parent.createContext(openTelemetry).attach()
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit { tracer.startSpan("foo").end() }.get(1, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
            scope.detach()
        }
        assertNull(spanService.createdSpans.single().parent)
    }

    @Test
    fun `span is ended with error status when action throws`() {
        val exc = IllegalStateException("boom")
        val thrown = assertThrows(IllegalStateException::class.java) {
            tracer.startSpan("foo") {
                throw exc
            }
        }
        assertSame(exc, thrown)
        with(spanService.createdSpans.single()) {
            assertFalse(isRecording)
            assertTrue(status is StatusData.Error)
        }
    }

    @Test
    fun `span creation failure returns an invalid span`() {
        val errorHandler = FakeErrorHandler(throwOnSdkError = false)
        val failingTracer = EmbTracer(
            impl = sdkTracer,
            spanService = object : SpanService by spanService {
                override fun createSpan(otelSpanStartArgs: OtelSpanStartArgs): EmbraceSdkSpan = error("boom")
            },
            clock = openTelemetryClock,
            openTelemetry = openTelemetry,
            errorHandler = errorHandler,
        )
        assertFalse(failingTracer.startSpan("foo").isRecording())
        assertEquals(SdkErrorType.SpanOperationFail.toString(), errorHandler.sdkErrorMessages.single().msg)
    }
}
