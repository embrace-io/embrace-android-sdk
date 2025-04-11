package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Methods that enable integration with the the large OTel ecosystem through standard OTel APIs and concepts.
 */
@InternalApi
public interface OTelApi {

    /**
     * Add a [LogRecordExporter] that OTel Logs will be exported to after logging
     */
    public fun addLogRecordExporter(logRecordExporter: LogRecordExporter)

    /**
     * Adds a [SpanExporter] that OTel Spans will be exported to after completion
     */
    public fun addSpanExporter(spanExporter: SpanExporter)

    /**
     * Returns an [OpenTelemetry] that provides working [Tracer] implementations that will record spans that fit into the Embrace data
     * model.
     */
    public fun getOpenTelemetry(): OpenTelemetry

    /**
     * Set an attribute on the [Resource] used by the OTel SDK instance with the given [AttributeKey] key and String value.
     * The value set will override any value set previously or by the Embrace SDK.
     * This must be called before the SDK is started in order for it to take effect.
     */
    public fun setResourceAttribute(
        key: AttributeKey<String>,
        value: String
    ): Unit = setResourceAttribute(key.key, value)

    /**
     * Set an attribute on the [Resource] used by the OTel SDK instance with the given String key and value.
     * The value set will override any value set previously or by the Embrace SDK.
     * This must be called before the SDK is started in order for it to take effect.
     */
    public fun setResourceAttribute(key: String, value: String)
}
