package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaOpenTelemetry
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanExporter

/**
 * Methods that enable integration with the the large OTel ecosystem through standard OTel APIs and concepts.
 */
@InternalApi
public interface OTelApi {

    /**
     * Add a [LogRecordExporter] that OTel Logs will be exported to after logging
     */
    public fun addLogRecordExporter(logRecordExporter: OtelJavaLogRecordExporter)

    /**
     * Adds a [SpanExporter] that OTel Spans will be exported to after completion
     */
    public fun addSpanExporter(spanExporter: OtelJavaSpanExporter)

    /**
     * Returns an [OpenTelemetry] that provides working [Tracer] implementations that will record spans that fit into the Embrace data
     * model.
     */
    public fun getOpenTelemetry(): OtelJavaOpenTelemetry

    /**
     * Set an attribute on the [Resource] used by the OTel SDK instance with the given [AttributeKey] key and String value.
     * The value set will override any value set previously or by the Embrace SDK.
     * This must be called before the SDK is started in order for it to take effect.
     */
    public fun setResourceAttribute(
        key: OtelJavaAttributeKey<String>,
        value: String
    ): Unit = setResourceAttribute(key.key, value)

    /**
     * Set an attribute on the [Resource] used by the OTel SDK instance with the given String key and value.
     * The value set will override any value set previously or by the Embrace SDK.
     * This must be called before the SDK is started in order for it to take effect.
     */
    public fun setResourceAttribute(key: String, value: String)
}
