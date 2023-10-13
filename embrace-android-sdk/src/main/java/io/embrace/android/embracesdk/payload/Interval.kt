package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Represents a value over a particular interval. This is used for:
 *
 *  * Periods during which the device was charging
 *  * Periods during which the device was connected to Wifi, WAN, or no network
 *
 */
internal data class Interval @JvmOverloads constructor(
    @SerializedName("st") val startTime: Long,
    @SerializedName("en") val endTime: Long,
    @SerializedName("v") val value: String? = null
)
