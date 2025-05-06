package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider

/**
 * Module that instantiates various OpenTelemetry related components
 */
@OptIn(ExperimentalApi::class)
interface OpenTelemetryModule {

    /**
     * Configuration for the OpenTelemetry SDK
     */
    val otelSdkConfig: OtelSdkConfig

    /**
     * Setup configuration for redacting sensitive keys
     */
    fun setupSensitiveKeysBehavior(sensitiveKeysBehavior: SensitiveKeysBehavior)

    /**
     * Caches span instances that are in progress or completed in the current session
     */
    val spanRepository: SpanRepository

    /**
     * Provides storage for completed spans that have not been sent off-device
     */
    val spanSink: SpanSink

    /**
     * An instance of the OpenTelemetry component obtained from the wrapped SDK to create spans
     */
    val sdkTracer: Tracer

    /**
     * Component that manages and provides access to the current session span
     */
    val currentSessionSpan: CurrentSessionSpan

    /**
     * Service to record spans
     */
    val spanService: SpanService

    /**
     * Implementation of public tracing API
     */
    val embraceTracer: EmbraceTracer

    /**
     * Implementation of internal tracing API
     */
    val internalTracer: InternalTracer

    /**
     * An instance of the OpenTelemetry component obtained from the wrapped SDK to create log records
     */
    val logger: Logger

    /**
     * Provides storage for completed logs that have not been forwarded yet to the delivery service
     */
    val logSink: LogSink

    /**
     * Provides an [OpenTelemetry] instance that can be used by instrumentation libraries to record telemetry as if it were using the
     * Embrace APIs. Currently, only the APIs related [Tracer] have operational implementations. Every other method will return no-op
     * implementations that records no data.
     */
    val externalOpenTelemetry: OpenTelemetry

    /**
     * Provides [Tracer] instances for instrumentation external to the Embrace SDK to create spans
     */
    val externalTracerProvider: TracerProvider

    /**
     * OpenTelemetry SDK compatible clock based on the internal Embrace clock instance
     */
    val openTelemetryClock: io.opentelemetry.sdk.common.Clock
}
