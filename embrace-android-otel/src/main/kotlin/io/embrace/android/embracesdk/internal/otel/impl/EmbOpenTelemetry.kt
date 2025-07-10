package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.tracing.TracerProvider

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
@OptIn(ExperimentalApi::class)
class EmbOpenTelemetry(
    private val impl: OpenTelemetry,
    override val clock: Clock,
    traceProviderSupplier: () -> TracerProvider,
) : OpenTelemetry by impl {

    override val tracerProvider: TracerProvider = traceProviderSupplier()
}
