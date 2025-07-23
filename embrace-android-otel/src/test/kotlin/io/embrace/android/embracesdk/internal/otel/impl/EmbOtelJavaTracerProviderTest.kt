package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTracerProvider
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbOtelJavaTracerProviderTest {
    private val clock = FakeClock()
    private val openTelemetryClock = FakeOtelKotlinClock(clock)

    private lateinit var spanService: FakeSpanService
    private lateinit var sdkTracerProvider: FakeTracerProvider
    private lateinit var embTracerProvider: EmbOtelJavaTracerProvider

    @Before
    fun setup() {
        spanService = FakeSpanService()
        sdkTracerProvider = FakeTracerProvider()
        embTracerProvider = EmbOtelJavaTracerProvider(
            sdkTracerProvider = sdkTracerProvider,
            spanService = spanService,
            clock = openTelemetryClock
        )
    }

    @Test
    fun `same instrumentation scope names return the same tracer instance`() {
        val tracer = embTracerProvider.get("foo")
        assertTrue(tracer is EmbOtelJavaTracer)
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
