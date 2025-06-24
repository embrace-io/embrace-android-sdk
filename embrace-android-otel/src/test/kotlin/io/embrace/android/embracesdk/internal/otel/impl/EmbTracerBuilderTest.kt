package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.internal.otel.sdk.TracerKey
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbTracerBuilderTest {

    @Test
    fun `check tracer attributes from builder`() {
        val expectedKey = TracerKey("foo", "v1", "url")
        val embTracerBuilder = EmbOtelJavaTracerBuilder(
            instrumentationScopeName = "foo",
            tracerSupplier = ::createTracer
        )

        val tracer = embTracerBuilder.setSchemaUrl("url").setInstrumentationVersion("v1").build() as FakeTracer
        assertEquals(expectedKey, tracer.tracerKey)
    }

    private fun createTracer(key: TracerKey): FakeTracer = FakeTracer(tracerKey = key)
}
