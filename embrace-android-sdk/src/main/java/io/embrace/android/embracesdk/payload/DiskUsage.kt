package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName

/**
 * Disk space used by the app and available memory on the device.
 */
internal data class DiskUsage(

    /**
     * Amount of disk space consumed by the app in bytes.
     */
    @SerializedName("as")
    val appDiskUsage: Long?,

    /**
     * Amount of disk space free on the device in bytes.
     */
    @SerializedName("fs")
    val deviceDiskFree: Long?
)
