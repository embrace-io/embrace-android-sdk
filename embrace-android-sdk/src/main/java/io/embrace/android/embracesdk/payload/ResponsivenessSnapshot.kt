package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data structure representing statistics tracking how responsive a component is, measured by looking at the duration of the gaps
 * between pings.
 */
@JsonClass(generateAdapter = true)
internal data class ResponsivenessSnapshot(
    @Json(name = "name")
    val name: String,

    @Json(name = "first")
    val firstPing: Long,

    @Json(name = "last")
    val lastPing: Long,

    @Json(name = "gaps")
    val gaps: Map<String, Long>,

    @Json(name = "outliers")
    val outliers: List<ResponsivenessOutlier>,

    @Json(name = "errors")
    val errors: Long,
)
