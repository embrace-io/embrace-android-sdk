package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fixtures.fakeContextKey
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.otelSpanBuilderWrapper
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
        val wrapper = tracer.otelSpanBuilderWrapper(
            name = "test",
            type = EmbType.Performance.Default,
            internal = true,
            private = true,
            parent = null,
        )
        val originalStartTime = clock.now()
        wrapper.startTimeMs = originalStartTime
        val startTime = clock.tick()
        with(wrapper.embraceAttributes.toSet()) {
            assertTrue(contains(PrivateSpan))
            assertTrue(contains(EmbType.Performance.Default))
        }
        assertEquals("emb-test", wrapper.initialSpanName)
        wrapper.startSpan(startTime).assertFakeSpanBuilder(
            expectedName = "emb-test",
            expectedStartTimeMs = startTime
        )
        assertEquals(originalStartTime, wrapper.startTimeMs)
    }

    @Test
    fun `add parent after initial creation`() {
        val wrapper = tracer.otelSpanBuilderWrapper(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            parent = null,
        )
        val parent = FakeEmbraceSdkSpan.started()
        val parentContext = checkNotNull(parent.asNewContext()?.with(fakeContextKey, "value"))
        wrapper.setParentContext(parentContext)
        val startTime = clock.now()
        wrapper.startSpan(startTime).assertFakeSpanBuilder(
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
        val wrapper = tracer.otelSpanBuilderWrapper(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            parent = parent,
        )

        wrapper.setNoParent()
        with(wrapper.startSpan(startTime)) {
            assertFakeSpanBuilder(
                expectedName = "test",
                expectedStartTimeMs = startTime
            )
        }

        val uxSpanBuilder = tracer.otelSpanBuilderWrapper(
            name = "ux-test",
            type = EmbType.Ux.View,
            internal = false,
            private = false,
            parent = parent,
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
        val spanBuilder = tracer.otelSpanBuilderWrapper(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            parent = null,
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
        val spanBuilder = tracer.otelSpanBuilderWrapper(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            parent = null,
        )
        spanBuilder.customAttributes["test-key"] = "test-value"
        assertEquals("test-value", spanBuilder.customAttributes["test-key"])
    }

    @Test
    fun `context value propagated even if it does not context a span`() {
        val fakeRootContext = Context.root().with(fakeContextKey, "fake-value")
        val spanBuilder = tracer.otelSpanBuilderWrapper(
            name = "parent",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            parent = null,
        )

        spanBuilder.setParentContext(fakeRootContext)
        assertEquals("fake-value", spanBuilder.getParentContext().get(fakeContextKey))

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
