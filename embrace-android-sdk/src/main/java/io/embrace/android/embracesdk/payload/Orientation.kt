package io.embrace.android.embracesdk.payload

import android.content.res.Configuration
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class Orientation(
    @Json(name = "o") val orientation: String,
    @Json(name = "ts") val timestamp: Long
) {

    constructor(orientation: Int, timestamp: Long) : this(
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) "l" else "p",
        timestamp
    )

    val internalOrientation: Int
        get() = if (orientation == "l") Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
}
