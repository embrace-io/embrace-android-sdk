package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeTracerProvider
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetryInstance
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLoggerProvider
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracerProvider
import io.embrace.opentelemetry.kotlin.noop
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbOpenTelemetryTest {
    private lateinit var tracerProvider: FakeTracerProvider
    private lateinit var openTelemetry: EmbOpenTelemetry

    @Before
    fun setup() {
        tracerProvider = FakeTracerProvider()
        openTelemetry = EmbOpenTelemetry(OpenTelemetryInstance.noop()) { tracerProvider }
    }

    @Test
    fun `tracer provider is a real implementation`() {
        assertNotEquals(OtelJavaOpenTelemetry.noop(), openTelemetry)
        assertNotEquals(OtelJavaTracerProvider.noop(), openTelemetry.tracerProvider)
        assertNotEquals(OtelJavaLoggerProvider.noop(), openTelemetry.loggerProvider)
    }
}
