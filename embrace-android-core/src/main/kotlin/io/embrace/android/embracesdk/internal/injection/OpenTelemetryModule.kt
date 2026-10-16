package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.spans.TracingApi

/**
 * Module that instantiates various OpenTelemetry related components
 */
interface OpenTelemetryModule {

    /**
     * Configuration for the OpenTelemetry SDK
     */
    val otelSdkConfig: OtelSdkConfig

    /**
     * Stores the spans of the current session: in-progress span instances as well as completed
     * spans that have not been sent off-device
     */
    val spanRepository: SpanRepository

    /**
     * Component that manages and provides access to the current session part span
     */
    val currentSessionPartSpan: CurrentSessionPartSpan

    /**
     * Service to record spans
     */
    val spanService: SpanService

    /**
     * Implementation of public tracing API
     */
    val tracingApi: TracingApi

    /**
     * Provides storage for completed logs that have not been forwarded yet to the delivery service
     */
    val logSink: LogSink

    /**
     * Provides a wrapper around commonly used OTel APIs in the SDK.
     */
    val otelSdkWrapper: OtelSdkWrapper
}
