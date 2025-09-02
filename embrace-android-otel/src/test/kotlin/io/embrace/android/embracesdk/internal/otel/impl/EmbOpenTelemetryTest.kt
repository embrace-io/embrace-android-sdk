package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeTracerProvider
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.noopOpenTelemetry
import org.junit.Assert.assertEquals
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
        openTelemetry = EmbOpenTelemetry(noopOpenTelemetry()) { tracerProvider }
    }

    @Test
    fun `tracer provider is a real implementation`() {
        val instance = noopOpenTelemetry()
        assertNotEquals(instance, openTelemetry)
        assertNotEquals(instance.tracerProvider, openTelemetry.tracerProvider)
        assertEquals(instance.loggerProvider, openTelemetry.loggerProvider)
    }
}
