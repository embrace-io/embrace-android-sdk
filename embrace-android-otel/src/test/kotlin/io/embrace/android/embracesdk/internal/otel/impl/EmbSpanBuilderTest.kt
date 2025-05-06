package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fixtures.fakeContextKey
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.otelSpanBuilderWrapper
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanBuilderWrapper
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

internal class EmbSpanBuilderTest {
    private val clock = FakeClock()
    private val openTelemetryClock = FakeOpenTelemetryClock(clock)
    private lateinit var tracer: FakeTracer
    private lateinit var spanService: FakeSpanService
    private lateinit var otelSpanBuilderWrapper: OtelSpanBuilderWrapper
    private lateinit var embSpanBuilder: EmbSpanBuilder

    @Before
    fun setup() {
        spanService = FakeSpanService()
        tracer = FakeTracer()
        otelSpanBuilderWrapper = tracer.otelSpanBuilderWrapper(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            parent = null,
        )
        embSpanBuilder = EmbSpanBuilder(
            otelSpanBuilderWrapper = otelSpanBuilderWrapper,
            spanService = spanService,
            clock = openTelemetryClock
        )
    }

    @Test
    fun `parameters set on embrace span builder respected`() {
        val spanParent = FakeEmbraceSdkSpan.started()
        val newOtelSpanBuilderWrapper = tracer.otelSpanBuilderWrapper(
            name = "custom",
            type = EmbType.Performance.Default,
            internal = true,
            private = true,
            parent = spanParent,
        )
        embSpanBuilder = EmbSpanBuilder(
            otelSpanBuilderWrapper = newOtelSpanBuilderWrapper,
            spanService = spanService,
            clock = openTelemetryClock
        )
        embSpanBuilder.startSpan()
        val fakeCreatedSpan = spanService.createdSpans.single()
        with(fakeCreatedSpan) {
            assertEquals(checkNotNull(spanParent.spanContext?.traceId), spanContext?.traceId)
            assertEquals(spanParent, parent)
            assertEquals("emb-custom", name)
            assertEquals(EmbType.Performance.Default, type)
        }
    }

    @Test
    fun `set parent to root`() {
        val oldParent = FakeEmbraceSdkSpan.started(
            parentContext = Context.root().with(
                fakeContextKey,
                "value"
            )
        )
        val newOtelSpanBuilderWrapper = tracer.otelSpanBuilderWrapper(
            name = "custom",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            parent = oldParent,
        )
        embSpanBuilder = EmbSpanBuilder(
            otelSpanBuilderWrapper = newOtelSpanBuilderWrapper,
            spanService = spanService,
            clock = openTelemetryClock
        )
        embSpanBuilder.setNoParent()
        embSpanBuilder.startSpan()
        val fakeCreatedSpan = spanService.createdSpans.single()
        with(fakeCreatedSpan) {
            assertNotEquals(checkNotNull(oldParent.spanContext?.traceId), spanContext?.traceId)
            assertNull(parent)
            assertNull(checkNotNull(asNewContext()).get(fakeContextKey))
            assertEquals("custom", name)
        }
    }

    @Test
    fun `replace parent`() {
        val newParentSpan = FakeEmbraceSdkSpan.started(
            parentContext = Context.root().with(fakeContextKey, "value")
        )
        val newParentContext = checkNotNull(newParentSpan.asNewContext())

        val newOtelSpanBuilderWrapper = tracer.otelSpanBuilderWrapper(
            name = "custom",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            parent = null,
        )
        embSpanBuilder = EmbSpanBuilder(
            otelSpanBuilderWrapper = newOtelSpanBuilderWrapper,
            spanService = spanService,
            clock = openTelemetryClock
        )
        embSpanBuilder.setParent(newParentContext)
        embSpanBuilder.startSpan()
        val fakeCreatedSpan = spanService.createdSpans.single()
        with(fakeCreatedSpan) {
            assertEquals(newParentSpan, parent)
            assertEquals(checkNotNull(newParentContext.getEmbraceSpan()?.traceId), spanContext?.traceId)
            assertEquals("value", checkNotNull(asNewContext()).get(fakeContextKey))
            assertEquals(checkNotNull(Span.fromContext(newParentContext).spanContext?.traceId), spanContext?.traceId)
        }
    }

    @Test
    fun `check start times set`() {
        val startTimeInstant = Instant.ofEpochMilli(clock.tick())
        embSpanBuilder.setStartTimestamp(startTimeInstant)
        assertEquals(startTimeInstant.toEpochMilli(), otelSpanBuilderWrapper.startTimeMs)
        val startTimeMs = clock.tick()
        embSpanBuilder.setStartTimestamp(startTimeMs, TimeUnit.MILLISECONDS)
        assertEquals(startTimeMs, otelSpanBuilderWrapper.startTimeMs)
    }

    @Test
    fun `attributes set properly`() {
        with(embSpanBuilder) {
            setAttribute("boolean", true)
            setAttribute("integer", 1)
            setAttribute("long", 2L)
            setAttribute("double", 3.0)
            setAttribute("string", "value")
            setAttribute(AttributeKey.booleanArrayKey("booleanArray"), listOf(true, false))
            setAttribute(AttributeKey.longArrayKey("integerArray"), listOf(1, 2))
            setAttribute(AttributeKey.longArrayKey("longArray"), listOf(2L, 3L))
            setAttribute(AttributeKey.doubleArrayKey("doubleArray"), listOf(3.0, 4.0))
            setAttribute(AttributeKey.stringArrayKey("stringArray"), listOf("value", "vee"))
        }

        assertEquals(10, otelSpanBuilderWrapper.customAttributes.count())
    }

    @Test
    fun `set span kind`() {
        embSpanBuilder.setSpanKind(SpanKind.CLIENT)
        val fakeSpan = otelSpanBuilderWrapper.startSpan(clock.now()) as FakeSpan
        assertEquals(SpanKind.CLIENT, fakeSpan.fakeSpanBuilder.spanKind)
    }

    @Test
    fun `attempting to add span links do not error out`() {
        embSpanBuilder.addLink(checkNotNull(FakeEmbraceSdkSpan.started().spanContext))
        embSpanBuilder.addLink(
            checkNotNull(FakeEmbraceSdkSpan.started().spanContext),
            Attributes.of(AttributeKey.stringKey("key"), "value")
        )
    }
}
