package io.embrace.android.embracesdk.payload

import android.content.res.Configuration
import com.google.gson.annotations.SerializedName

internal data class Orientation(
    @SerializedName("o") val orientation: String,
    @SerializedName("ts") val timestamp: Long
) {

    constructor(orientation: Int, timestamp: Long) : this(
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) "l" else "p",
        timestamp
    )

    val internalOrientation: Int
        get() = if (orientation == "l") Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
}
