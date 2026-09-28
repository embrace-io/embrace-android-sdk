package io.embrace.android.gradle.plugin.instrumentation.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AutomaticDataCaptureLocalConfig(

    @SerialName("power_save_mode_info")
    val powerSaveModeServiceEnabled: Boolean? = null,

    @SerialName("network_connectivity_info")
    val networkConnectivityServiceEnabled: Boolean? = null,

    @SerialName("anr_info")
    val threadBlockageServiceEnabled: Boolean? = null,

    @SerialName("ui_load_tracing_disabled")
    val uiLoadPerfTracingDisabled: Boolean? = null,

    @SerialName("ui_load_tracing_selected_only")
    val uiLoadPerfTracingSelectedOnly: Boolean? = null,

    @SerialName("end_startup_with_app_ready")
    val endStartupWithAppReadyEnabled: Boolean? = null,

    @SerialName("activity_process_lifecycle_tracker_enabled")
    val activityProcessLifecycleTrackerEnabled: Boolean? = null,
) : java.io.Serializable {

    companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
