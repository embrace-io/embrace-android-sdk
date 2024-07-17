package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
public class AnrLocalConfig(
    @Json(name = "capture_google")
    public val captureGoogle: Boolean? = null,

    @Json(name = "capture_unity_thread")
    public val captureUnityThread: Boolean? = null
)
