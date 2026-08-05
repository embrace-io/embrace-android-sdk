package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration values relating to data capture of the SDK
 */
@Serializable
data class DataRemoteConfig(

    @SerialName("pct_thermal_status_enabled")
    val pctThermalStatusEnabled: Float? = null,

    /**
     * The maximum number of spans created via the public API that can be recorded in a session part.
     */
    @SerialName("max_custom_spans_per_session")
    val maxCustomSpansPerSession: Int? = null,

    /**
     * The maximum number of spans created by the SDK's own instrumentation, other than network request spans,
     * that can be recorded in a session part.
     */
    @SerialName("max_internal_spans_per_session")
    val maxInternalSpansPerSession: Int? = null,

    /**
     * The maximum number of network request spans that can be recorded in a session part.
     */
    @SerialName("max_network_spans_per_session")
    val maxNetworkSpansPerSession: Int? = null,

    /**
     * The maximum number of non-breadcrumb span events that may be added to a session part span.
     * Breadcrumbs are counted against a separate limit - see [UiRemoteConfig.breadcrumbs].
     */
    @SerialName("max_span_events_per_session_part")
    val maxSpanEventsPerSessionPart: Int? = null,
)
