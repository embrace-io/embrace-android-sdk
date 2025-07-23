package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeOtelJavaTracer
import io.embrace.android.embracesdk.internal.otel.sdk.TracerKey
import org.junit.Assert.assertEquals
import org.junit.Test

internal class EmbOtelJavaTracerBuilderTest {

    @Test
    fun `check tracer attributes from builder`() {
        val expectedKey = TracerKey("foo", "v1", "url")
        val embTracerBuilder = EmbOtelJavaTracerBuilder(
            instrumentationScopeName = "foo",
            tracerSupplier = ::createTracer
        )

        val tracer = embTracerBuilder.setSchemaUrl("url").setInstrumentationVersion("v1").build() as FakeOtelJavaTracer
        assertEquals(expectedKey, tracer.tracerKey)
    }

    private fun createTracer(key: TracerKey): FakeOtelJavaTracer = FakeOtelJavaTracer(tracerKey = key)
}
