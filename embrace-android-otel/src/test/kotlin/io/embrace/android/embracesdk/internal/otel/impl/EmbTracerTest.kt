package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.otel.spans.createContext
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.tracing.SpanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class EmbTracerTest {
    private val clock = FakeClock()
    private val openTelemetryClock = FakeOtelKotlinClock(clock)
    private val openTelemetry = fakeOpenTelemetry()

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
}
