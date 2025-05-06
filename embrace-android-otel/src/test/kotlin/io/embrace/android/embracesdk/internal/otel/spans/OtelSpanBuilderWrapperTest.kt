package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fixtures.fakeContextKey
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class OtelSpanBuilderWrapperTest {
    private val clock = FakeClock()
    private lateinit var tracer: FakeTracer

    @Before
    fun setup() {
        tracer = FakeTracer()
    }

    @Test
    fun `check private and internal span creation`() {
        val spanBuilder = OtelSpanBuilderWrapper(
            tracer = tracer,
            name = "test",
            telemetryType = EmbType.Performance.Default,
            internal = true,
            private = true,
            parentSpan = null,
        )
        val originalStartTime = clock.now()
        spanBuilder.startTimeMs = originalStartTime
        val startTime = clock.tick()
        with(spanBuilder.getEmbraceAttributes().toSet()) {
            assertTrue(contains(PrivateSpan))
            assertTrue(contains(EmbType.Performance.Default))
        }
        assertEquals("emb-test", spanBuilder.spanName)
        spanBuilder.startSpan(startTime).assertFakeSpanBuilder(
            expectedName = "emb-test",
            expectedStartTimeMs = startTime
        )
        assertEquals(originalStartTime, spanBuilder.startTimeMs)
    }

    @Test
    fun `add parent after initial creation`() {
        val spanBuilder = OtelSpanBuilderWrapper(
            tracer = tracer,
            name = "test",
            telemetryType = EmbType.Performance.Default,
            internal = false,
            private = false,
            parentSpan = null,
        )
        val parent = FakeEmbraceSdkSpan.started()
        val parentContext = checkNotNull(parent.asNewContext()?.with(fakeContextKey, "value"))
        spanBuilder.setParentContext(parentContext)
        val startTime = clock.now()
        spanBuilder.startSpan(startTime).assertFakeSpanBuilder(
            expectedName = "test",
            expectedParentContext = parentContext,
            expectedStartTimeMs = startTime,
            expectedTraceId = parent.spanContext?.traceId
        )
    }

    @Test
    fun `remove parent after initial creation`() {
        val parent = FakeEmbraceSdkSpan.started()
        val startTime = clock.now()
        val spanBuilder = OtelSpanBuilderWrapper(
            tracer = tracer,
            name = "test",
            telemetryType = EmbType.Performance.Default,
            internal = false,
            private = false,
            parentSpan = parent,
        )

        spanBuilder.setNoParent()
        with(spanBuilder.startSpan(startTime)) {
            assertFakeSpanBuilder(
                expectedName = "test",
                expectedStartTimeMs = startTime
            )
        }

        val uxSpanBuilder = OtelSpanBuilderWrapper(
            tracer = tracer,
            name = "ux-test",
            telemetryType = EmbType.Ux.View,
            internal = false,
            private = false,
            parentSpan = parent,
        )

        uxSpanBuilder.setNoParent()
        with(uxSpanBuilder.startSpan(startTime)) {
            assertFakeSpanBuilder(
                expectedName = "ux-test",
                expectedStartTimeMs = startTime
            )
        }
    }

    @Test
    fun `add span kind`() {
        val spanBuilder = OtelSpanBuilderWrapper(
            tracer = tracer,
            name = "test",
            telemetryType = EmbType.Performance.Default,
            internal = false,
            private = false,
            parentSpan = null,
        )
        val startTime = clock.now()
        spanBuilder.setSpanKind(SpanKind.CLIENT)
        spanBuilder.startSpan(startTime).assertFakeSpanBuilder(
            expectedName = "test",
            expectedStartTimeMs = startTime,
            expectedSpanKind = SpanKind.CLIENT
        )
    }

    @Test
    fun `custom attribute setting`() {
        val spanBuilder = OtelSpanBuilderWrapper(
            tracer = tracer,
            name = "test",
            telemetryType = EmbType.Performance.Default,
            internal = false,
            private = false,
            parentSpan = null,
        )
        spanBuilder.setCustomAttribute("test-key", "test-value")
        assertEquals("test-value", spanBuilder.getCustomAttributes()["test-key"])
    }

    @Test
    fun `context value propagated even if it does not context a span`() {
        val fakeRootContext = Context.root().with(fakeContextKey, "fake-value")
        val spanBuilder = OtelSpanBuilderWrapper(
            tracer = tracer,
            name = "parent",
            telemetryType = EmbType.Performance.Default,
            internal = false,
            private = false,
            parentSpan = null,
        )

        spanBuilder.setParentContext(fakeRootContext)
        assertEquals("fake-value", spanBuilder.parentContext.get(fakeContextKey))

        val span = spanBuilder.startSpan(clock.now()) as FakeSpan
        assertEquals("fake-value", span.fakeSpanBuilder.parentContext.get(fakeContextKey))
    }

    private fun Span.assertFakeSpanBuilder(
        expectedName: String,
        expectedParentContext: Context = Context.root(),
        expectedSpanKind: SpanKind? = null,
        expectedStartTimeMs: Long,
        expectedTraceId: String? = null,
    ) {
        val fakeSpan = this as FakeSpan
        with(fakeSpan.fakeSpanBuilder) {
            assertEquals(expectedName, spanName)
            assertEquals(expectedParentContext, parentContext)
            assertEquals(expectedSpanKind, spanKind)
            assertEquals(expectedStartTimeMs, startTimestampMs)
            if (expectedTraceId != null) {
                assertEquals(expectedTraceId, spanContext.traceId)
            }
        }
    }
}
