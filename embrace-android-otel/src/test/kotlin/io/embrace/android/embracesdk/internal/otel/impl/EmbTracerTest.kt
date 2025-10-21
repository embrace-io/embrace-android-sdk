package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fakes.TestConstants.TESTS_DEFAULT_USE_KOTLIN_SDK
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.createNoopOpenTelemetry
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
            openTelemetry = fakeOpenTelemetry(),
            useKotlinSdk = TESTS_DEFAULT_USE_KOTLIN_SDK
        )
    }

    @Test
    fun `check span generated with default parameters`() {
        tracer.createSpan("foo").end()
        val fakeCreatedSpan = spanService.createdSpans.single()
        with(fakeCreatedSpan) {
            assertNull(parent)
            assertEquals("foo", name)
            assertEquals(EmbType.Performance.Default, type)
        }
    }

    @Test
    fun `check span generated with non default parameters`() {
        val parentCtx = createNoopOpenTelemetry().contextFactory.root()
        tracer.createSpan(
            "foo",
            parentContext = parentCtx,
            spanKind = SpanKind.CLIENT,
            startTimestamp = 500L.nanosToMillis()
        ) {
            setStringAttribute("foo", "bar")
        }
        val fakeCreatedSpan = spanService.createdSpans.single()
        with(fakeCreatedSpan) {
            assertEquals(parentCtx, parentContext)
            assertEquals("foo", name)
            assertEquals(SpanKind.CLIENT, spanKind)
            assertEquals("bar", attributes["foo"])
        }
    }
}
