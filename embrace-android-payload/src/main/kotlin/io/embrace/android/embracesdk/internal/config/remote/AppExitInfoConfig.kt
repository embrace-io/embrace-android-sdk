package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class AppExitInfoConfig(
    /**
     * Defines the max size of bytes to allow capturing AppExitInfo ndk/anr traces
     */
    @Json(name = "app_exit_info_traces_limit") val appExitInfoTracesLimit: Int? = null,

    @Json(name = "pct_aei_enabled_v2") val pctAeiCaptureEnabled: Float? = null,

    @Json(name = "aei_max_num") val aeiMaxNum: Int? = null,
)
