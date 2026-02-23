package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.TracerProvider

@OptIn(ExperimentalApi::class)
class FakeTracerProvider : TracerProvider {
    override fun getTracer(
        name: String,
        version: String?,
        schemaUrl: String?,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    ): Tracer = FakeTracer()
}
