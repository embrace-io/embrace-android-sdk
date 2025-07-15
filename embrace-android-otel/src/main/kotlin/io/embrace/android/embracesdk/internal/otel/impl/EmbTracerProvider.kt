package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.tracing.Tracer
import io.embrace.opentelemetry.kotlin.tracing.TracerProvider

/**
 * Embrace-specific decorator that adds extra logic to OTel Tracing.
 */
@OptIn(ExperimentalApi::class)
class EmbTracerProvider(
    api: OpenTelemetry,
) : TracerProvider {

    private val impl: TracerProvider = api.tracerProvider

    override fun getTracer(
        name: String,
        version: String?,
        schemaUrl: String?,
        attributes: AttributeContainer.() -> Unit,
    ): Tracer {
        return impl.getTracer(
            name = name,
            version = version,
            schemaUrl = schemaUrl,
            attributes = attributes
        )
    }
}
