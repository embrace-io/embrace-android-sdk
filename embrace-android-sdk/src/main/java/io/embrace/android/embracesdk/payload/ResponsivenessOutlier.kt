package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents an outlier in terms of the gap between when 2 pings to a component are responded to. The [startMs] and [endMs] timestamps
 * denote the two points in time when the pings are recorded.
 */
@JsonClass(generateAdapter = true)
internal data class ResponsivenessOutlier(
    @Json(name = "start")
    val startMs: Long,

    @Json(name = "end")
    val endMs: Long
)
