package io.embrace.android.embracesdk.internal.opentelemetry

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTracerProvider
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbTracerProviderTest {
    private val clock = FakeClock()
    private val openTelemetryClock = FakeOpenTelemetryClock(clock)

    private lateinit var spanService: FakeSpanService
    private lateinit var sdkTracerProvider: FakeTracerProvider
    private lateinit var embTracerProvider: EmbTracerProvider

    @Before
    fun setup() {
        spanService = FakeSpanService()
        sdkTracerProvider = FakeTracerProvider()
        embTracerProvider = EmbTracerProvider(
            sdkTracerProvider = sdkTracerProvider,
            spanService = spanService,
            clock = openTelemetryClock
        )
    }

    @Test
    fun `same instrumentation scope names return the same tracer instance`() {
        val tracer = embTracerProvider.get("foo")
        assertTrue(tracer is EmbTracer)
        val dupeTracer = embTracerProvider.get("foo")
        val differentTracer = embTracerProvider.get("food")
        assertSame(tracer, dupeTracer)
        assertNotSame(tracer, differentTracer)
    }

    @Test
    fun `same instrumentation scope version return the same tracer instance`() {
        val tracer = embTracerProvider.get("foo", "v1")
        val dupeTracer = embTracerProvider.get("foo", "v1")
        val differentTracer = embTracerProvider.get("foo", "v2")
        assertSame(tracer, dupeTracer)
        assertNotSame(tracer, differentTracer)
    }

    @Test
    fun `same instrumentation schema url returns the same tracer instance`() {
        val tracer = embTracerProvider.tracerBuilder("foo").setSchemaUrl("url1").build()
        val dupeTracer = embTracerProvider.tracerBuilder("foo").setSchemaUrl("url1").build()
        val differentTracer = embTracerProvider.tracerBuilder("foo").setSchemaUrl("url2").build()
        assertSame(tracer, dupeTracer)
        assertNotSame(tracer, differentTracer)
    }
}
