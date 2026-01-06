package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.logging.LoggerProvider
import io.embrace.opentelemetry.kotlin.tracing.TracerProvider

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
@OptIn(ExperimentalApi::class)
class EmbOpenTelemetry(
    private val impl: OpenTelemetry,
    traceProviderSupplier: () -> TracerProvider,
    loggerProviderSupplier: () -> LoggerProvider,
) : OpenTelemetry by impl {
    override val clock: Clock = impl.clock
    override val tracerProvider: TracerProvider = traceProviderSupplier()
    override val loggerProvider: LoggerProvider = loggerProviderSupplier()
}
