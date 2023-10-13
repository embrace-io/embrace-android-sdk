package io.embrace.android.embracesdk.config.remote

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.config.behavior.AppExitInfoBehavior.Companion.AEI_MAX_NUM_DEFAULT

internal class AppExitInfoConfig(
    /**
     * Defines the max size of bytes to allow capturing AppExitInfo ndk/anr traces
     */
    @SerializedName("app_exit_info_traces_limit")
    val appExitInfoTracesLimit: Int? = null,

    @SerializedName("pct_aei_enabled_v2")
    val pctAeiCaptureEnabled: Float? = null,

    @SerializedName("aei_max_num")
    val aeiMaxNum: Int = AEI_MAX_NUM_DEFAULT,
)
