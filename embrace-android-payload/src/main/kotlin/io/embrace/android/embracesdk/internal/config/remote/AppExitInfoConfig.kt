package io.embrace.android.embracesdk.internal.config.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class AppExitInfoConfig(
    /**
     * Defines the max size of bytes to allow capturing AppExitInfo ndk/anr traces
     */
    @Json(name = "app_exit_info_traces_limit")
    public val appExitInfoTracesLimit: Int? = null,

    @Json(name = "pct_aei_enabled_v2")
    public val pctAeiCaptureEnabled: Float? = null,

    @Json(name = "aei_max_num")
    public val aeiMaxNum: Int? = null,
)
