package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.logs.EventService
import io.embrace.android.embracesdk.internal.otel.sdk.ApiKey
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.kotlin.logging.LoggerProvider
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalApi::class)
class EmbLoggerProvider(
    private val otelImpl: OpenTelemetry,
    private val eventService: EventService,
) : LoggerProvider {

    private val loggers = ConcurrentHashMap<ApiKey, Logger>()

    override fun getLogger(
        name: String,
        version: String?,
        schemaUrl: String?,
        attributes: (MutableAttributeContainer.() -> Unit)?,
    ): Logger {
        val key = ApiKey(
            instrumentationScopeName = name,
            instrumentationScopeVersion = version,
            schemaUrl = schemaUrl
        )

        val logger = (
            loggers[key]
                ?: synchronized(loggers) {
                    return loggers[key] ?: createLogger(key)
                }
            )
        return logger
    }

    private fun createLogger(key: ApiKey): Logger {
        val loggerImpl = otelImpl.loggerProvider.getLogger(
            name = key.instrumentationScopeName,
            version = key.instrumentationScopeVersion,
            schemaUrl = key.schemaUrl
        )
        val logger = EmbLogger(loggerImpl, eventService)
        loggers[key] = logger
        return logger
    }
}
