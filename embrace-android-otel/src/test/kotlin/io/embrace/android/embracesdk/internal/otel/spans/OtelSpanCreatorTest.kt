package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOtelJavaTracer
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanBuilder
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
internal class OtelSpanCreatorTest {

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
        val args = OtelSpanStartArgs(
            name = "test",
            type = EmbType.Performance.Default,
            internal = true,
            private = true,
        )
        val creator = OtelSpanCreator(
            tracer = TracerAdapter(tracer, otelClock),
            spanStartArgs = args,
        )
        val originalStartTime = clock.now()
        args.startTimeMs = originalStartTime
        val startTime = clock.tick()
        with(args.embraceAttributes.toSet()) {
            assertTrue(contains(PrivateSpan))
            assertTrue(contains(EmbType.Performance.Default))
        }
        assertEquals("emb-test", args.spanName)
        assertNull(creator.spanStartArgs.getParentSpanContext())

        creator.startSpan(startTime).assertSpan(
            expectedName = "emb-test",
            expectedStartTimeMs = startTime
        )
        assertEquals(originalStartTime, args.startTimeMs)
    }

    @Test
    fun `add parent after initial creation`() {
        val args = OtelSpanStartArgs(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
        )
        val creator = OtelSpanCreator(
            tracer = TracerAdapter(tracer, otelClock),
            spanStartArgs = args,
        )
        val parent = FakeSpanBuilder(spanName = "parent").startSpan()
        args.parentContext = OtelJavaContext.root().with(parent)
        assertEquals(parent.spanContext, creator.spanStartArgs.getParentSpanContext())
        val startTime = clock.now()
        creator.startSpan(startTime).assertSpan(
            expectedName = "test",
            expectedStartTimeMs = startTime,
        )
    }

    @Test
    fun `change and remove parent after initial creation`() {
        val parent = FakeEmbraceSdkSpan.started()
        val startTime = clock.now()
        val args = OtelSpanStartArgs(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            parentSpan = parent,
        )

        assertEquals(parent.spanContext, args.getParentSpanContext())

        val creator = OtelSpanCreator(
            tracer = TracerAdapter(tracer, otelClock),
            spanStartArgs = args,
        )

        val newParent = FakeSpanBuilder("new-parent").startSpan()
        args.parentContext = OtelJavaContext.root().with(newParent)
        assertEquals(newParent.spanContext, creator.spanStartArgs.getParentSpanContext())

        creator.startSpan(startTime).assertSpan(
            expectedName = "test",
            expectedStartTimeMs = startTime
        )

        val uxArgs = OtelSpanStartArgs(
            name = "ux-test",
            type = EmbType.Ux.View,
            internal = false,
            private = false,
            parentSpan = parent,
        )
        val uxCreator = OtelSpanCreator(
            tracer = TracerAdapter(tracer, otelClock),
            spanStartArgs = uxArgs,
        )

        uxArgs.parentContext = OtelJavaContext.root()
        assertNull(uxCreator.spanStartArgs.getParentSpanContext())
        uxCreator.startSpan(startTime).assertSpan(
            expectedName = "ux-test",
            expectedStartTimeMs = startTime
        )
    }

    @Test
    fun `add span kind`() {
        val args = OtelSpanStartArgs(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
        )
        val creator = OtelSpanCreator(
            tracer = TracerAdapter(tracer, otelClock),
            spanStartArgs = args
        )

        val startTime = clock.now()
        args.spanKind = SpanKind.CLIENT
        creator.startSpan(startTime).assertSpan(
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
        )
        args.customAttributes["test-key"] = "test-value"
        assertEquals("test-value", args.customAttributes["test-key"])
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
