package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeOtelJavaTracerProvider
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContextPropagators
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLoggerProvider
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracerProvider
import io.opentelemetry.api.metrics.MeterProvider
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class EmbOpenTelemetryTest {
    private lateinit var tracerProvider: FakeOtelJavaTracerProvider
    private lateinit var openTelemetry: EmbOtelJavaOpenTelemetry

    @Before
    fun setup() {
        tracerProvider = FakeOtelJavaTracerProvider()
        openTelemetry = EmbOtelJavaOpenTelemetry { tracerProvider }
    }

    @Test
    fun `tracer provider is a real implementation`() {
        assertNotEquals(OtelJavaOpenTelemetry.noop(), openTelemetry)
        assertNotEquals(OtelJavaTracerProvider.noop(), openTelemetry.tracerProvider)
        assertSame(MeterProvider.noop(), openTelemetry.meterProvider)
        assertSame(OtelJavaLoggerProvider.noop(), openTelemetry.logsBridge)
        assertSame(OtelJavaContextPropagators.noop(), openTelemetry.propagators)
    }
}
