package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.createCompatOpenTelemetryInstance
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
            openTelemetry = createCompatOpenTelemetryInstance(),
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
}
