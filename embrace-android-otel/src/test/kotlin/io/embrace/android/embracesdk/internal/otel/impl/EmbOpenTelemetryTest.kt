package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeLoggerProvider
import io.embrace.android.embracesdk.fakes.FakeTracerProvider
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.NoopOpenTelemetry
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbOpenTelemetryTest {
    private lateinit var tracerProvider: FakeTracerProvider
    private lateinit var loggerProvider: FakeLoggerProvider
    private lateinit var openTelemetry: EmbOpenTelemetry

    @Before
    fun setup() {
        tracerProvider = FakeTracerProvider()
        loggerProvider = FakeLoggerProvider()
        openTelemetry = EmbOpenTelemetry(
            impl = NoopOpenTelemetry,
            traceProviderSupplier = { tracerProvider },
            loggerProviderSupplier = { loggerProvider },
        )
    }

    @Test
    fun `tracer and logger providers are real implementations`() {
        val instance = NoopOpenTelemetry
        assertNotEquals(instance, openTelemetry)
        assertNotEquals(instance.tracerProvider, openTelemetry.tracerProvider)
        assertNotEquals(instance.loggerProvider, openTelemetry.loggerProvider)
    }
}
