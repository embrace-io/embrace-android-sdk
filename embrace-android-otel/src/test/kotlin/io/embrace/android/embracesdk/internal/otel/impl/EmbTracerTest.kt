package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fakes.fakeObjectCreator
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.tracing.model.SpanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbTracerTest {
    private val clock = FakeClock()
    private val openTelemetryClock = FakeOtelKotlinClock(clock)

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
            objectCreator = fakeObjectCreator,
        )
    }

    @Test
    fun `check span generated with default parameters`() {
        tracer.createSpan("foo").end()
        val now = clock.now()
        val fakeCreatedSpan = spanService.createdSpans.single()
        with(fakeCreatedSpan) {
            assertNull(parent)
            assertEquals("foo", name)
            assertEquals(SpanKind.INTERNAL, spanKind)
            assertEquals(EmbType.Performance.Default, type)
            assertEquals(now, spanStartTimeMs)
        }
    }

    @Test
    fun `check span generated with non default parameters`() {
        val parentCtx = fakeObjectCreator.context.root()
        tracer.createSpan(
            "foo",
            parentContext = parentCtx,
            spanKind = SpanKind.CLIENT,
            startTimestamp = 700L
        ) {
            setStringAttribute("foo", "bar")
        }
        val fakeCreatedSpan = spanService.createdSpans.single()
        with(fakeCreatedSpan) {
            assertEquals(parentCtx, parentContext)
            assertEquals("foo", name)
            assertEquals(SpanKind.CLIENT, spanKind)
            assertEquals("bar", attributes["foo"])
            assertEquals(700L, spanStartTimeMs)
        }
    }
}
