package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
data class AutomaticDataCaptureLocalConfig(

    @Json(name = "power_save_mode_info")
    @SerialName("power_save_mode_info")
    val powerSaveModeServiceEnabled: Boolean? = null,

    @Json(name = "network_connectivity_info")
    @SerialName("network_connectivity_info")
    val networkConnectivityServiceEnabled: Boolean? = null,

    @Json(name = "anr_info")
    @SerialName("anr_info")
    val threadBlockageServiceEnabled: Boolean? = null,

    @Json(name = "ui_load_tracing_disabled")
    @SerialName("ui_load_tracing_disabled")
    val uiLoadPerfTracingDisabled: Boolean? = null,

    @Json(name = "ui_load_tracing_selected_only")
    @SerialName("ui_load_tracing_selected_only")
    val uiLoadPerfTracingSelectedOnly: Boolean? = null,

    @Json(name = "end_startup_with_app_ready")
    @SerialName("end_startup_with_app_ready")
    val endStartupWithAppReadyEnabled: Boolean? = null,

    @Json(name = "activity_process_lifecycle_tracker_enabled")
    @SerialName("activity_process_lifecycle_tracker_enabled")
    val activityProcessLifecycleTrackerEnabled: Boolean? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
