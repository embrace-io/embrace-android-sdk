package io.embrace.android.embracesdk.internal.config.local

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal class AnrLocalConfig(
    @Json(name = "capture_google")
    val captureGoogle: Boolean? = null,

    @Json(name = "capture_unity_thread")
    val captureUnityThread: Boolean? = null
)
