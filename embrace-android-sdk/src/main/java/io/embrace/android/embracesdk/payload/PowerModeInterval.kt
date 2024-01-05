package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Represents a value over a particular interval. This is used for:
 *
 *  * Periods during which the device was in power save mode
 *
 */
@JsonClass(generateAdapter = true)
internal data class PowerModeInterval @JvmOverloads constructor(
    @Json(name = "st") val startTime: Long,
    @Json(name = "en") val endTime: Long? = null
)
