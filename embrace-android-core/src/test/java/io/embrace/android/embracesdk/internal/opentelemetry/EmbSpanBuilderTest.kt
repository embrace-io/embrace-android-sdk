package io.embrace.android.embracesdk.internal.opentelemetry

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.fakes.FakePersistableEmbraceSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fixtures.fakeContextKey
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanBuilder
import io.embrace.android.embracesdk.internal.spans.getEmbraceSpan
import io.embrace.android.embracesdk.spans.AutoTerminationMode
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
    private lateinit var embraceSpanBuilder: EmbraceSpanBuilder
    private lateinit var embSpanBuilder: EmbSpanBuilder

    @Before
    fun setup() {
        spanService = FakeSpanService()
        tracer = FakeTracer()
        embraceSpanBuilder = EmbraceSpanBuilder(
            tracer = tracer,
            name = "test",
            telemetryType = EmbType.Performance.Default,
            internal = false,
            private = false,
            parentSpan = null,
        )
        embSpanBuilder = EmbSpanBuilder(
            embraceSpanBuilder = embraceSpanBuilder,
            spanService = spanService,
            clock = openTelemetryClock
        )
    }

    @Test
    fun `parameters set on embrace span builder respected`() {
        val spanParent = FakePersistableEmbraceSpan.started()
        val newEmbraceSpanBuilder = EmbraceSpanBuilder(
            tracer = tracer,
            name = "custom",
            telemetryType = EmbType.Performance.Default,
            internal = true,
            private = true,
            parentSpan = spanParent,
            autoTerminationMode = AutoTerminationMode.ON_BACKGROUND,
        )
        embSpanBuilder = EmbSpanBuilder(
            embraceSpanBuilder = newEmbraceSpanBuilder,
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
            assertEquals(AutoTerminationMode.ON_BACKGROUND, autoTerminationMode)
        }
    }

    @Test
    fun `set parent to root`() {
        val oldParent = FakePersistableEmbraceSpan.started(
            parentContext = Context.root().with(
                fakeContextKey,
                "value"
            )
        )
        val newEmbraceSpanBuilder = EmbraceSpanBuilder(
            tracer = tracer,
            name = "custom",
            telemetryType = EmbType.Performance.Default,
            internal = false,
            private = false,
            parentSpan = oldParent,
        )
        embSpanBuilder = EmbSpanBuilder(
            embraceSpanBuilder = newEmbraceSpanBuilder,
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
        val newParentSpan = FakePersistableEmbraceSpan.started(
            parentContext = Context.root().with(fakeContextKey, "value")
        )
        val newParentContext = checkNotNull(newParentSpan.asNewContext())

        val newEmbraceSpanBuilder = EmbraceSpanBuilder(
            tracer = tracer,
            name = "custom",
            telemetryType = EmbType.Performance.Default,
            internal = false,
            private = false,
            parentSpan = null,
        )
        embSpanBuilder = EmbSpanBuilder(
            embraceSpanBuilder = newEmbraceSpanBuilder,
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
        assertEquals(startTimeInstant.toEpochMilli(), embraceSpanBuilder.startTimeMs)
        val startTimeMs = clock.tick()
        embSpanBuilder.setStartTimestamp(startTimeMs, TimeUnit.MILLISECONDS)
        assertEquals(startTimeMs, embraceSpanBuilder.startTimeMs)
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

        assertEquals(10, embraceSpanBuilder.getCustomAttributes().count())
    }

    @Test
    fun `set span kind`() {
        embSpanBuilder.setSpanKind(SpanKind.CLIENT)
        val fakeSpan = embraceSpanBuilder.startSpan(clock.now()) as FakeSpan
        assertEquals(SpanKind.CLIENT, fakeSpan.fakeSpanBuilder.spanKind)
    }

    @Test
    fun `attempting to add span links do not error out`() {
        embSpanBuilder.addLink(checkNotNull(FakePersistableEmbraceSpan.started().spanContext))
        embSpanBuilder.addLink(
            checkNotNull(FakePersistableEmbraceSpan.started().spanContext),
            Attributes.of(AttributeKey.stringKey("key"), "value")
        )
    }
}
