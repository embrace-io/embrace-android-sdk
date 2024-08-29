package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class AutomaticDataCaptureLocalConfig(
    @Json(name = "memory_info")
    public val memoryServiceEnabled: Boolean? = null,

    @Json(name = "power_save_mode_info")
    public val powerSaveModeServiceEnabled: Boolean? = null,

    @Json(name = "network_connectivity_info")
    public val networkConnectivityServiceEnabled: Boolean? = null,

    @Json(name = "anr_info")
    public val anrServiceEnabled: Boolean? = null
)
