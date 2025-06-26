package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContextPropagators
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTracerProvider

/**
 * Embrace-specific implementation that can be used to obtain working Tracer implementations that will record spans for Embrace sessions
 */
class EmbOtelJavaOpenTelemetry(
    private val traceProviderSupplier: () -> OtelJavaTracerProvider,
) : OtelJavaOpenTelemetry {

    override fun getTracerProvider(): OtelJavaTracerProvider = traceProviderSupplier()

    override fun getPropagators(): OtelJavaContextPropagators = OtelJavaContextPropagators.noop()
}
