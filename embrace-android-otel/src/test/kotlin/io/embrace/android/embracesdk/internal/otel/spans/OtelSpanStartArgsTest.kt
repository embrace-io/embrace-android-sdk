package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelJavaTracer
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanBuilder
import io.embrace.android.embracesdk.fixtures.TOO_LONG_INTERNAL_SPAN_NAME
import io.embrace.android.embracesdk.fixtures.TOO_LONG_SPAN_NAME
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.k2j.tracing.TracerAdapter
import io.embrace.opentelemetry.kotlin.tracing.model.Span
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class OtelSpanStartArgsTest {

    private lateinit var clock: FakeClock
    private lateinit var otelClock: Clock
    private lateinit var tracer: FakeOtelJavaTracer

    @Before
    fun setup() {
        tracer = FakeOtelJavaTracer()
        clock = FakeClock()
        otelClock = FakeOtelKotlinClock(clock)
    }

    @Test
    fun `check private and internal span creation`() {
        val originalStartTime = clock.now()
        val args = OtelSpanStartArgs(
            name = "test",
            type = EmbType.Performance.Default,
            internal = true,
            private = true,
            tracer = TracerAdapter(tracer, otelClock),
            startTimeMs = clock.now()
        )
        val startTime = clock.tick()
        with(args.embraceAttributes.toSet()) {
            assertTrue(contains(PrivateSpan))
            assertTrue(contains(EmbType.Performance.Default))
        }
        assertEquals("emb-test", args.initialSpanName)
        assertNull(args.getParentSpanContext())

        args.startSpan(startTime).assertSpan(
            expectedName = "emb-test",
            expectedStartTimeMs = startTime
        )
        assertEquals(originalStartTime, args.startTimeMs)
    }

    @Test
    fun `add parent after initial creation`() {
        val parent = FakeSpanBuilder(spanName = "parent").startSpan()
        val args = OtelSpanStartArgs(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            tracer = TracerAdapter(tracer, otelClock),
            parentCtx = OtelJavaContext.root().with(parent)
        )
        assertEquals(parent.spanContext, args.getParentSpanContext())
        val startTime = clock.now()
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
            tracer = TracerAdapter(tracer, otelClock),
            spanKind = SpanKind.CLIENT
        )
        val startTime = clock.now()
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
            tracer = TracerAdapter(tracer, otelClock),
        )
        args.customAttributes["test-key"] = "test-value"
        assertEquals("test-value", args.customAttributes["test-key"])
    }

    @Test
    fun `initial name not truncated`() {
        val tracer = TracerAdapter(tracer, otelClock)
        val startTime = clock.now()

        val creator = OtelSpanStartArgs(
            tracer = tracer,
            name = TOO_LONG_SPAN_NAME,
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
        )

        creator.startSpan(startTime).assertSpan(
            expectedName = TOO_LONG_SPAN_NAME,
            expectedStartTimeMs = startTime
        )

        val internalSpanCreator = OtelSpanStartArgs(
            tracer = tracer,
            name = TOO_LONG_INTERNAL_SPAN_NAME,
            type = EmbType.Performance.Default,
            internal = true,
            private = false,
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
