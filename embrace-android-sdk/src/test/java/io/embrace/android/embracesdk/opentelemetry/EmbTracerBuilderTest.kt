package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.internal.opentelemetry.EmbTracerBuilder
import io.embrace.android.embracesdk.internal.opentelemetry.TracerKey
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbTracerBuilderTest {

    @Test
    fun `check tracer attributes from builder`() {
        val expectedKey = TracerKey("foo", "v1", "url")
        val embTracerBuilder = EmbTracerBuilder(
            instrumentationScopeName = "foo",
            tracerSupplier = ::createTracer
        )

        val tracer = embTracerBuilder.setSchemaUrl("url").setInstrumentationVersion("v1").build() as FakeTracer
        assertEquals(expectedKey, tracer.tracerKey)
    }

    private fun createTracer(key: TracerKey): FakeTracer = FakeTracer(tracerKey = key)
}
