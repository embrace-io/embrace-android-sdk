package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetryInstance
import io.embrace.opentelemetry.kotlin.k2j.tracing.TracerAdapter
import io.embrace.opentelemetry.kotlin.kotlinApi
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbTracerProviderTest {

    private lateinit var spanService: FakeSpanService
    private lateinit var embTracerProvider: EmbTracerProvider

    @Before
    fun setup() {
        spanService = FakeSpanService()
        embTracerProvider = EmbTracerProvider(
            api = OpenTelemetryInstance.kotlinApi(),
        )
    }

    @Test
    fun `same instrumentation scope names return the same tracer instance`() {
        val tracer = embTracerProvider.getTracer("foo")
        assertTrue(tracer is TracerAdapter)
        val dupeTracer = embTracerProvider.getTracer("foo")
        val differentTracer = embTracerProvider.getTracer("food")
        assertSame(tracer, dupeTracer)
        assertNotSame(tracer, differentTracer)
    }

    @Test
    fun `same instrumentation scope version return the same tracer instance`() {
        val tracer = embTracerProvider.getTracer("foo", "v1")
        val dupeTracer = embTracerProvider.getTracer("foo", "v1")
        val differentTracer = embTracerProvider.getTracer("foo", "v2")
        assertSame(tracer, dupeTracer)
        assertNotSame(tracer, differentTracer)
    }

    @Test
    fun `same instrumentation schema url returns the same tracer instance`() {
        val tracer = embTracerProvider.getTracer("foo", schemaUrl = "url1")
        val dupeTracer = embTracerProvider.getTracer("foo", schemaUrl = "url1")
        val differentTracer = embTracerProvider.getTracer("foo", schemaUrl = "url2")
        assertSame(tracer, dupeTracer)
        assertNotSame(tracer, differentTracer)
    }
}
