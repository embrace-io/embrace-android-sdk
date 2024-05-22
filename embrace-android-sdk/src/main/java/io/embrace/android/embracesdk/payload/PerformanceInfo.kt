package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Describes information about how the device is performing.
 */
@JsonClass(generateAdapter = true)
internal data class PerformanceInfo(

    /**
     * Current disk space usage of the app, and free space on the device.
     */
    @Json(name = "ds")
    val diskUsage: DiskUsage? = null
)
