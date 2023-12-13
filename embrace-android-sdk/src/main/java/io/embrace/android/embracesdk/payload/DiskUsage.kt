package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Disk space used by the app and available memory on the device.
 */
@JsonClass(generateAdapter = true)
internal data class DiskUsage(

    /**
     * Amount of disk space consumed by the app in bytes.
     */
    @Json(name = "as")
    val appDiskUsage: Long?,

    /**
     * Amount of disk space free on the device in bytes.
     */
    @Json(name = "fs")
    val deviceDiskFree: Long?
)
