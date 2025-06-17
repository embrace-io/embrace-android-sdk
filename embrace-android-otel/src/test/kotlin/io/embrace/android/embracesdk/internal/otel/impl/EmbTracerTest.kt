package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeKotlinTracer
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbTracerTest {
    private val clock = FakeClock()
    private val openTelemetryClock = FakeOpenTelemetryClock(clock)

    private lateinit var spanService: FakeSpanService
    private lateinit var sdkTracer: FakeKotlinTracer
    private lateinit var tracer: EmbTracer

    @Before
    fun setup() {
        spanService = FakeSpanService()
        sdkTracer = FakeKotlinTracer()
        tracer = EmbTracer(
            sdkTracer = sdkTracer,
            spanService = spanService,
            clock = openTelemetryClock,
        )
    }

    @Test
    fun `check span generated with default parameters`() {
        tracer.spanBuilder("foo").startSpan().end()
        val fakeCreatedSpan = spanService.createdSpans.single()
        with(fakeCreatedSpan) {
            assertNull(parent)
            assertEquals("foo", name)
            assertEquals(EmbType.Performance.Default, type)
        }
    }
}
