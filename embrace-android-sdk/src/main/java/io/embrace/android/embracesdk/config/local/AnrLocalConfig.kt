package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

internal class AnrLocalConfig(
    @SerializedName("capture_google")
    val captureGoogle: Boolean? = null,

    @SerializedName("capture_unity_thread")
    val captureUnityThread: Boolean? = null
)
