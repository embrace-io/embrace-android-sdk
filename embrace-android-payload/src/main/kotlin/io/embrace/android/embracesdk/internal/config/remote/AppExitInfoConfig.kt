package io.embrace.android.embracesdk.internal.config.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AppExitInfoConfig(
    /**
     * Defines the max size of bytes to allow capturing AppExitInfo ndk/anr traces
     */
    @SerialName("app_exit_info_traces_limit")
    val appExitInfoTracesLimit: Int? = null,

    @SerialName("pct_aei_enabled_v2")
    val pctAeiCaptureEnabled: Float? = null,

    @SerialName("aei_max_num")
    val aeiMaxNum: Int? = null,
)
