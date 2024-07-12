package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal class AutomaticDataCaptureLocalConfig(
    @Json(name = "memory_info")
    val memoryServiceEnabled: Boolean? = null,

    @Json(name = "power_save_mode_info")
    val powerSaveModeServiceEnabled: Boolean? = null,

    @Json(name = "network_connectivity_info")
    val networkConnectivityServiceEnabled: Boolean? = null,

    @Json(name = "anr_info")
    val anrServiceEnabled: Boolean? = null
)
