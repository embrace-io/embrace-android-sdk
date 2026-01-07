package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.embrace.opentelemetry.kotlin.logging.LoggerProvider

@OptIn(ExperimentalApi::class)
class FakeLoggerProvider : LoggerProvider {
    override fun getLogger(
        name: String,
        version: String?,
        schemaUrl: String?,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    ): Logger = FakeOpenTelemetryLogger()
}
