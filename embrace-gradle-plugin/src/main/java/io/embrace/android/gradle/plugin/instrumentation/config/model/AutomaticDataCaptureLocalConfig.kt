package io.embrace.android.gradle.plugin.instrumentation.config.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class AutomaticDataCaptureLocalConfig(
    @Json(name = "memory_info")
    val memoryServiceEnabled: Boolean? = null,

    @Json(name = "power_save_mode_info")
    val powerSaveModeServiceEnabled: Boolean? = null,

    @Json(name = "network_connectivity_info")
    val networkConnectivityServiceEnabled: Boolean? = null,

    @Json(name = "anr_info")
    val anrServiceEnabled: Boolean? = null,

    @Json(name = "ui_load_tracing_disabled")
    val uiLoadPerfTracingDisabled: Boolean? = null,

    @Json(name = "ui_load_tracing_selected_only")
    val uiLoadPerfTracingSelectedOnly: Boolean? = null,

    @Json(name = "end_startup_with_app_ready")
    val endStartupWithAppReadyEnabled: Boolean? = null,
) : Serializable {

    private companion object {
        @Suppress("ConstPropertyName")
        private const val serialVersionUID = 1L
    }
}
