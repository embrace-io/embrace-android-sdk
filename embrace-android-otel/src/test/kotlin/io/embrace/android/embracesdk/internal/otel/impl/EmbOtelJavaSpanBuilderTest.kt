package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOtelJavaTracer
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fixtures.fakeContextKey
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.spans.OtelSpanStartArgs
import io.embrace.android.embracesdk.internal.otel.spans.createContext
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanKind
import io.embrace.opentelemetry.kotlin.k2j.tracing.TracerAdapter
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalApi::class)
internal class EmbOtelJavaSpanBuilderTest {
    private val clock = FakeClock()
    private val openTelemetryClock = FakeOtelKotlinClock(clock)
    private lateinit var tracer: Tracer
    private lateinit var spanService: FakeSpanService
    private lateinit var args: OtelSpanStartArgs
    private lateinit var embSpanBuilder: EmbOtelJavaSpanBuilder

    @Before
    fun setup() {
        spanService = FakeSpanService()
        tracer = TracerAdapter(FakeOtelJavaTracer(), openTelemetryClock)
        args = OtelSpanStartArgs(
            tracer = tracer,
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
        )
        embSpanBuilder = EmbOtelJavaSpanBuilder(
            otelSpanStartArgs = args,
            spanService = spanService,
            clock = openTelemetryClock
        )
    }

    @Test
    fun `parameters set on embrace span builder respected`() {
        val spanParent = FakeEmbraceSdkSpan.started()
        val newOtelSpanStartArgs = OtelSpanStartArgs(
            tracer = tracer,
            name = "custom",
            type = EmbType.Performance.Default,
            internal = true,
            private = true,
            parentCtx = spanParent.createContext(),
            autoTerminationMode = AutoTerminationMode.ON_BACKGROUND,
        )
        embSpanBuilder = EmbOtelJavaSpanBuilder(
            otelSpanStartArgs = newOtelSpanStartArgs,
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
            parentContext = OtelJavaContext.root().with(
                fakeContextKey,
                "value"
            )
        )
        val newOtelSpanStartArgs = OtelSpanStartArgs(
            tracer = tracer,
            name = "custom",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
            parentCtx = oldParent.createContext(),
        )
        embSpanBuilder = EmbOtelJavaSpanBuilder(
            otelSpanStartArgs = newOtelSpanStartArgs,
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
            parentContext = OtelJavaContext.root().with(fakeContextKey, "value")
        )
        val newParentContext = checkNotNull(newParentSpan.asNewContext())

        val newOtelSpanStartArgs = OtelSpanStartArgs(
            tracer = tracer,
            name = "custom",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
        )
        embSpanBuilder = EmbOtelJavaSpanBuilder(
            otelSpanStartArgs = newOtelSpanStartArgs,
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
            assertEquals(checkNotNull(OtelJavaSpan.fromContext(newParentContext).spanContext?.traceId), spanContext?.traceId)
        }
    }

    @Test
    fun `check start times set`() {
        val startTimeInstant = Instant.ofEpochMilli(clock.tick())
        embSpanBuilder.setStartTimestamp(startTimeInstant)
        assertEquals(startTimeInstant.toEpochMilli(), args.startTimeMs)
        val startTimeMs = clock.tick()
        embSpanBuilder.setStartTimestamp(startTimeMs, TimeUnit.MILLISECONDS)
        assertEquals(startTimeMs, args.startTimeMs)
    }

    @Test
    fun `attributes set properly`() {
        with(embSpanBuilder) {
            setAttribute("boolean", true)
            setAttribute("integer", 1)
            setAttribute("long", 2L)
            setAttribute("double", 3.0)
            setAttribute("string", "value")
            setAttribute(OtelJavaAttributeKey.booleanArrayKey("booleanArray"), listOf(true, false))
            setAttribute(OtelJavaAttributeKey.longArrayKey("integerArray"), listOf(1, 2))
            setAttribute(OtelJavaAttributeKey.longArrayKey("longArray"), listOf(2L, 3L))
            setAttribute(OtelJavaAttributeKey.doubleArrayKey("doubleArray"), listOf(3.0, 4.0))
            setAttribute(OtelJavaAttributeKey.stringArrayKey("stringArray"), listOf("value", "vee"))
        }

        assertEquals(10, args.customAttributes.count())
    }

    @Test
    fun `set span kind`() {
        embSpanBuilder.setSpanKind(OtelJavaSpanKind.CLIENT)
        val fakeSpan = args.startSpan(clock.now())
        assertEquals(SpanKind.CLIENT, fakeSpan.spanKind)
    }

    @Test
    fun `attempting to add span links do not error out`() {
        embSpanBuilder.addLink(checkNotNull(FakeEmbraceSdkSpan.started().spanContext))
        embSpanBuilder.addLink(
            checkNotNull(FakeEmbraceSdkSpan.started().spanContext),
            OtelJavaAttributes.of(OtelJavaAttributeKey.stringKey("key"), "value")
        )
    }
}
