package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

internal class AutomaticDataCaptureLocalConfig(
    @SerializedName("memory_info")
    val memoryServiceEnabled: Boolean? = null,

    @SerializedName("power_save_mode_info")
    val powerSaveModeServiceEnabled: Boolean? = null,

    @SerializedName("network_connectivity_info")
    val networkConnectivityServiceEnabled: Boolean? = null,

    @SerializedName("anr_info")
    val anrServiceEnabled: Boolean? = null
)
