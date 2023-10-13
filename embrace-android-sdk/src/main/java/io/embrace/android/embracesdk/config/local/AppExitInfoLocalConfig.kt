package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

internal class AppExitInfoLocalConfig(
    /**
     * Defines the max size of bytes to allow capturing AppExitInfo ndk/anr traces
     */
    @SerializedName("app_exit_info_traces_limit")
    val appExitInfoTracesLimit: Int? = null,

    @SerializedName("aei_enabled")
    val aeiCaptureEnabled: Boolean? = null
)
