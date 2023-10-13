package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Represents a value over a particular interval. This is used for:
 *
 *  * Periods during which the device was in power save mode
 *
 */
internal data class PowerModeInterval @JvmOverloads constructor(
    @SerializedName("st") val startTime: Long,
    @SerializedName("en") val endTime: Long? = null
)
