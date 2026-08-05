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
     * The maximum number of non-breadcrumb span events that may be added to a session part span.
     * Breadcrumbs are counted against a separate limit - see [UiRemoteConfig.breadcrumbs].
     */
    @SerialName("max_span_events_per_session_part")
    val maxSpanEventsPerSessionPart: Int? = null,
)
