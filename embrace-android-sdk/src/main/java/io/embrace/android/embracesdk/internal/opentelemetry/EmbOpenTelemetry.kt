package io.embrace.android.embracesdk.internal.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.propagation.ContextPropagators

/**
 * Embrace-specific implementation that can be used to obtain working Tracer implementations that will record spans for Embrace sessions
 */
internal class EmbOpenTelemetry(
    private val traceProviderSupplier: () -> TracerProvider
) : OpenTelemetry {

    override fun getTracerProvider(): TracerProvider = traceProviderSupplier()

    override fun getPropagators(): ContextPropagators = ContextPropagators.noop()
}
