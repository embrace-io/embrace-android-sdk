package io.embrace.android.embracesdk.opentelemetry

import io.embrace.android.embracesdk.fakes.FakeTracerProvider
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.logs.LoggerProvider
import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.propagation.ContextPropagators
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class EmbOpenTelemetryTest {
    private lateinit var tracerProvider: FakeTracerProvider
    private lateinit var openTelemetry: EmbOpenTelemetry

    @Before
    fun setup() {
        tracerProvider = FakeTracerProvider()
        openTelemetry = EmbOpenTelemetry { tracerProvider }
    }

    @Test
    fun `tracer provider is a real implementation`() {
        assertNotEquals(OpenTelemetry.noop(), openTelemetry)
        assertNotEquals(TracerProvider.noop(), openTelemetry.tracerProvider)
        assertSame(MeterProvider.noop(), openTelemetry.meterProvider)
        assertSame(LoggerProvider.noop(), openTelemetry.logsBridge)
        assertSame(ContextPropagators.noop(), openTelemetry.propagators)
    }
}
