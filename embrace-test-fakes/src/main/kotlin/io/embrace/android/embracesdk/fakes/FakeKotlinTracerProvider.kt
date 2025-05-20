package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.tracing.Tracer

@ExperimentalApi
class FakeKotlinTracerProvider : io.embrace.opentelemetry.kotlin.tracing.TracerProvider {
    override fun getTracer(
        name: String,
        version: String?,
        schemaUrl: String?,
        attributes: AttributeContainer.() -> Unit,
    ): Tracer = FakeKotlinTracer()
}
