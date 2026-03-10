package io.embrace.android.embracesdk.internal.otel.impl

import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.logging.LoggerProvider
import io.opentelemetry.kotlin.tracing.TracerProvider

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
class EmbOpenTelemetry(
    private val impl: OpenTelemetry,
    traceProviderSupplier: () -> TracerProvider,
    loggerProviderSupplier: () -> LoggerProvider,
) : OpenTelemetry by impl {
    override val clock: Clock = impl.clock
    override val tracerProvider: TracerProvider = traceProviderSupplier()
    override val loggerProvider: LoggerProvider = loggerProviderSupplier()
}
