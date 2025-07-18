package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.OpenTelemetry
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
     * Returns an [OpenTelemetry] instance that uses the API from opentelemetry-kotlin. This API
     * is currently experimental and is subject to breaking change without warning.
     */
    @ExperimentalApi
    public fun getOpenTelemetryKotlin(): OpenTelemetry

    /**
     * Set an attribute on the resource used by the OTel SDK instance with the given key and String value.
     * The value set will override any value set previously or by the Embrace SDK.
     * This must be called before the SDK is started in order for it to take effect.
     */
    @Deprecated(
        "Use setResourceAttribute(key: String, value: String) instead.",
        ReplaceWith("setResourceAttribute(key.key, value)")
    )
    public fun setResourceAttribute(
        key: OtelJavaAttributeKey<String>,
        value: String,
    ): Unit = setResourceAttribute(key.key, value)

    /**
     * Set an attribute on the resource used by the OTel SDK instance with the given String key and value.
     * The value set will override any value set previously or by the Embrace SDK.
     * This must be called before the SDK is started in order for it to take effect.
     */
    public fun setResourceAttribute(key: String, value: String)
}
