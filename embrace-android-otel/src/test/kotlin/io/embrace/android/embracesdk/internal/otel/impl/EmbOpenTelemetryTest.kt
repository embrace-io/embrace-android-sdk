package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeTracerProvider
import io.opentelemetry.kotlin.NoopOpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

internal class EmbOpenTelemetryTest {
    private lateinit var tracerProvider: FakeTracerProvider
    private lateinit var openTelemetry: EmbOpenTelemetry

    @Before
    fun setup() {
        tracerProvider = FakeTracerProvider()
        openTelemetry = EmbOpenTelemetry(
            impl = NoopOpenTelemetry,
            traceProviderSupplier = { tracerProvider },
        )
    }

    @Test
    fun `tracer provider is a real implementation`() {
        val instance = NoopOpenTelemetry
        assertNotEquals(instance, openTelemetry)
        assertNotEquals(instance.tracerProvider, openTelemetry.tracerProvider)
    }

    @Test
    fun `logger provider is not decorated`() {
        assertEquals(NoopOpenTelemetry.loggerProvider, openTelemetry.loggerProvider)
    }
}
