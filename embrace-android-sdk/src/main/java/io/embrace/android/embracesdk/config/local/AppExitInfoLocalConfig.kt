package io.embrace.android.embracesdk.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal class AppExitInfoLocalConfig(
    /**
     * Defines the max size of bytes to allow capturing AppExitInfo ndk/anr traces
     */
    @Json(name = "app_exit_info_traces_limit")
    val appExitInfoTracesLimit: Int? = null,

    @Json(name = "aei_enabled")
    val aeiCaptureEnabled: Boolean? = null
)
