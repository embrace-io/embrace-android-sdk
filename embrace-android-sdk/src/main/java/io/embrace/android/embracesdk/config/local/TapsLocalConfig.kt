package io.embrace.android.embracesdk.config.local

import com.google.gson.annotations.SerializedName

internal class TapsLocalConfig(
    @SerializedName("capture_coordinates")
    val captureCoordinates: Boolean? = null
)
