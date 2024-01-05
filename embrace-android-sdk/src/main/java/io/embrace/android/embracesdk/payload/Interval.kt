package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a value over a particular interval. This is used for:
 *
 *  * Periods during which the device was charging
 *  * Periods during which the device was connected to Wifi, WAN, or no network
 *
 */
@JsonClass(generateAdapter = true)
internal data class Interval @JvmOverloads constructor(
    @Json(name = "st") val startTime: Long,
    @Json(name = "en") val endTime: Long,
    @Json(name = "v") val value: String? = null
)
