package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fixtures.fakeContextKey
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class OtelSpanCreatorTest {
    private val clock = FakeClock()
    private lateinit var tracer: FakeTracer

    @Before
    fun setup() {
        tracer = FakeTracer()
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
            tracer = tracer,
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
        creator.startSpan(startTime).assertFakeSpanBuilder(
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
            tracer = tracer,
            spanStartArgs = args,
        )
        val parent = FakeEmbraceSdkSpan.started()
        val parentContext = checkNotNull(parent.asNewContext()?.with(fakeContextKey, "value"))
        args.parentContext = parentContext
        val startTime = clock.now()
        creator.startSpan(startTime).assertFakeSpanBuilder(
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
        val args = OtelSpanStartArgs(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            parentSpan = parent,
        )
        val creator = OtelSpanCreator(
            tracer = tracer,
            spanStartArgs = args,
        )

        args.parentContext = OtelJavaContext.root()
        with(creator.startSpan(startTime)) {
            assertFakeSpanBuilder(
                expectedName = "test",
                expectedStartTimeMs = startTime
            )
        }

        val uxArgs = OtelSpanStartArgs(
            name = "ux-test",
            type = EmbType.Ux.View,
            internal = false,
            private = false,
            parentSpan = parent,
        )
        val uxSpanBuilder = OtelSpanCreator(
            tracer = tracer,
            spanStartArgs = uxArgs,
        )

        uxArgs.parentContext = OtelJavaContext.root()
        with(uxSpanBuilder.startSpan(startTime)) {
            assertFakeSpanBuilder(
                expectedName = "ux-test",
                expectedStartTimeMs = startTime
            )
        }
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
            tracer = tracer,
            spanStartArgs = args
        )

        val startTime = clock.now()
        args.spanKind = OtelJavaSpanKind.CLIENT
        creator.startSpan(startTime).assertFakeSpanBuilder(
            expectedName = "test",
            expectedStartTimeMs = startTime,
            expectedSpanKind = OtelJavaSpanKind.CLIENT
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

    @Test
    fun `context value propagated even if it does not context a span`() {
        val fakeRootContext = OtelJavaContext.root().with(fakeContextKey, "fake-value")
        val args = OtelSpanStartArgs(
            name = "parent",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
        )
        val creator = OtelSpanCreator(
            tracer = tracer,
            spanStartArgs = args
        )

        args.parentContext = fakeRootContext
        assertEquals("fake-value", args.parentContext.get(fakeContextKey))

        val span = creator.startSpan(clock.now()) as FakeSpan
        assertEquals("fake-value", span.fakeSpanBuilder.parentContext.get(fakeContextKey))
    }

    private fun OtelJavaSpan.assertFakeSpanBuilder(
        expectedName: String,
        expectedParentContext: OtelJavaContext = OtelJavaContext.root(),
        expectedSpanKind: OtelJavaSpanKind? = null,
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
