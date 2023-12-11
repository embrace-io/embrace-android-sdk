package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Data structure representing statistics tracking how responsive a component is, measured by looking at the duration of the gaps
 * between pings.
 */
internal data class ResponsivenessSnapshot(
    @SerializedName("name")
    val name: String,

    @SerializedName("first")
    val firstPing: Long,

    @SerializedName("last")
    val lastPing: Long,

    @SerializedName("gaps")
    val gaps: Map<String, Long>,

    @SerializedName("outliers")
    val outliers: List<ResponsivenessOutlier>,

    @SerializedName("errors")
    val errors: Long,
)
