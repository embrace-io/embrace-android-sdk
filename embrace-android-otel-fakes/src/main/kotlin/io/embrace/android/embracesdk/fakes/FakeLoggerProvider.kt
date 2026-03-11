package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.LoggerProvider

class FakeLoggerProvider : LoggerProvider {
    override fun getLogger(
        name: String,
        version: String?,
        schemaUrl: String?,
        attributes: (AttributesMutator.() -> Unit)?,
    ): Logger = FakeOpenTelemetryLogger()
}
