package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Represents an outlier in terms of the gap between when 2 pings to a component are responded to. The [startMs] and [endMs] timestamps
 * denote the two points in time when the pings are recorded.
 */
internal data class ResponsivenessOutlier(
    @SerializedName("start")
    val startMs: Long,

    @SerializedName("end")
    val endMs: Long
)
