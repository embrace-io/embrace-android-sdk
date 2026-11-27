package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.annotation.InternalApi
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor

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
     * Adds a [SpanProcessor] that will process OTel Spans after Embrace's internal processor.
     * Processors must be added before the SDK has started or they will be ignored.
     */
    public fun addSpanProcessor(spanProcessor: SpanProcessor)

    /**
     * Adds a [LogRecordProcessor] that will process OTel Logs after Embrace's internal processor.
     * Processors must be added before the SDK has started or they will be ignored.
     */
    public fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor)

    /**
     * Returns an [OpenTelemetry] instance that uses the API from opentelemetry-kotlin. This API
     * is currently experimental and is subject to breaking change without warning.
     */
    public fun getOpenTelemetry(): OpenTelemetry

    /**
     * Returns an [OpenTelemetry] instance that uses the API from opentelemetry-kotlin.
     */
    @Deprecated("Use getOpenTelemetry() instead.", ReplaceWith("getOpenTelemetry()"))
    public fun getOpenTelemetryKotlin(): OpenTelemetry = getOpenTelemetry()

    /**
     * Set an attribute on the resource used by the OTel SDK instance with the given key and typed value.
     * The value keeps its type where OTel has a matching attribute type (for example String, Boolean, Long, Double,
     * or a List of one of those). Any other value is set as a String.
     * The value set will override any value set previously or by the Embrace SDK.
     * This must be called before the SDK is started in order for it to take effect.
     */
    public fun setResourceAttribute(key: String, value: Any)
}
