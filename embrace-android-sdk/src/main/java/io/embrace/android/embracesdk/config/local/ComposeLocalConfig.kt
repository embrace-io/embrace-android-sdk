package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

internal class ComposeLocalConfig(
    @SerializedName("capture_compose_onclick")
    val captureComposeOnClick: Boolean? = null
)
