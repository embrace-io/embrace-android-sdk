package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.fakeObjectCreator
import io.embrace.android.embracesdk.fixtures.TOO_LONG_INTERNAL_SPAN_NAME
import io.embrace.android.embracesdk.fixtures.TOO_LONG_SPAN_NAME
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.get
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetryInstance
import io.embrace.opentelemetry.kotlin.getTracer
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class OtelSpanStartArgsTest {

    private lateinit var clock: FakeClock
    private lateinit var otelClock: Clock
    private lateinit var tracer: Tracer

    @Before
    fun setup() {
        clock = FakeClock()
        otelClock = FakeOtelKotlinClock(clock)
        tracer = OpenTelemetryInstance.get(clock = otelClock).getTracer("test-tracer")
    }

    @Test
    fun `check private and internal span creation`() {
        val originalStartTime = otelClock.now()
        val args = OtelSpanStartArgs(
            name = "test",
            type = EmbType.Performance.Default,
            internal = true,
            private = true,
            tracer = tracer,
            startTimeMs = originalStartTime,
            objectCreator = fakeObjectCreator
        )
        val startTime = clock.tick()
        with(args.embraceAttributes.toSet()) {
            assertTrue(contains(PrivateSpan))
            assertTrue(contains(EmbType.Performance.Default))
        }
        assertEquals("emb-test", args.initialSpanName)
        val spanContext = fakeObjectCreator.span.fromContext(args.parentContext).spanContext
        assertFalse(spanContext.isValid)

        args.startSpan(startTime).assertSpan(
            expectedName = "emb-test",
            expectedStartTimeMs = startTime
        )
        assertEquals(originalStartTime, args.startTimeMs)
    }

    @Test
    fun `add parent after initial creation`() {
        val parent = tracer.createSpan("parent")
        val ctx = fakeObjectCreator.context.storeSpan(fakeObjectCreator.context.root(), parent)
        val args = OtelSpanStartArgs(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            tracer = tracer,
            parentCtx = ctx,
            objectCreator = fakeObjectCreator
        )
        val spanContext = fakeObjectCreator.span.fromContext(args.parentContext).spanContext
        assertEquals(parent.spanContext.traceId, spanContext.traceId)

        val startTime = otelClock.now()
        args.startSpan(startTime).assertSpan(
            expectedName = "test",
            expectedStartTimeMs = startTime,
        )
    }

    @Test
    fun `add span kind`() {
        val args = OtelSpanStartArgs(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            tracer = tracer,
            spanKind = SpanKind.CLIENT,
            objectCreator = fakeObjectCreator
        )
        val startTime = otelClock.now()
        args.startSpan(startTime).assertSpan(
            expectedName = "test",
            expectedStartTimeMs = startTime,
            expectedSpanKind = SpanKind.CLIENT
        )
    }

    @Test
    fun `custom attribute setting`() {
        val args = OtelSpanStartArgs(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            tracer = tracer,
            objectCreator = fakeObjectCreator
        )
        args.customAttributes["test-key"] = "test-value"
        assertEquals("test-value", args.customAttributes["test-key"])
    }

    @Test
    fun `initial name not truncated`() {
        val startTime = otelClock.now()

        val creator = OtelSpanStartArgs(
            name = TOO_LONG_SPAN_NAME,
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            tracer = tracer,
            objectCreator = fakeObjectCreator
        )

        creator.startSpan(startTime).assertSpan(
            expectedName = TOO_LONG_SPAN_NAME,
            expectedStartTimeMs = startTime
        )

        val internalSpanCreator = OtelSpanStartArgs(
            name = TOO_LONG_INTERNAL_SPAN_NAME,
            type = EmbType.Performance.Default,
            internal = true,
            private = false,
            tracer = tracer,
            objectCreator = fakeObjectCreator
        )

        internalSpanCreator.startSpan(startTime).assertSpan(
            expectedName = "emb-$TOO_LONG_INTERNAL_SPAN_NAME",
            expectedStartTimeMs = startTime
        )
    }

    private fun Span.assertSpan(
        expectedName: String,
        expectedSpanKind: SpanKind = SpanKind.INTERNAL,
        expectedStartTimeMs: Long,
    ) {
        assertEquals(expectedName, name)
        assertEquals(expectedSpanKind, spanKind)
        assertEquals(expectedStartTimeMs.millisToNanos(), startTimestamp)
    }
}
