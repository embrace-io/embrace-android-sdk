package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.logs.EventService
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.opentelemetry.kotlin.ExperimentalApi

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
     * Caches span instances that are in progress or completed in the current session
     */
    val spanRepository: SpanRepository

    /**
     * Provides storage for completed spans that have not been sent off-device
     */
    val spanSink: SpanSink

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
     * Service to record events
     */
    val eventService: EventService

    /**
     * Provides storage for completed logs that have not been forwarded yet to the delivery service
     */
    val logSink: LogSink

    /**
     * Provides a wrapper around commonly used OTel APIs in the SDK.
     */
    val otelSdkWrapper: OtelSdkWrapper

    /**
     * Setup configuration configuration-dependent behavior
     */
    fun applyConfiguration(
        sensitiveKeysBehavior: SensitiveKeysBehavior,
        bypassValidation: Boolean,
        otelBehavior: OtelBehavior,
    )
}
