package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class EmbTracerTest {
    private val clock = FakeClock()
    private val openTelemetryClock = FakeOpenTelemetryClock(clock)

    private lateinit var spanService: FakeSpanService
    private lateinit var sdkTracer: FakeTracer
    private lateinit var tracer: EmbOtelJavaTracer

    @Before
    fun setup() {
        spanService = FakeSpanService()
        sdkTracer = FakeTracer()
        tracer = EmbOtelJavaTracer(
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
