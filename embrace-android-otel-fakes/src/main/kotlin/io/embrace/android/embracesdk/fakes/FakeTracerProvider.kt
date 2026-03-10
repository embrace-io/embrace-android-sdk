package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.TracerProvider

class FakeTracerProvider : TracerProvider {
    override fun getTracer(
        name: String,
        version: String?,
        schemaUrl: String?,
        attributes: (AttributesMutator.() -> Unit)?,
    ): Tracer = FakeTracer()
}
