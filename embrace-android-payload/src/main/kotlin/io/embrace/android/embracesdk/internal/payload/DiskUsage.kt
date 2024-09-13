package io.embrace.android.embracesdk.internal.payload

/**
 * Disk space used by the app and available memory on the device.
 */
data class DiskUsage(

    /**
     * Amount of disk space consumed by the app in bytes.
     */
    val appDiskUsage: Long?,

    /**
     * Amount of disk space free on the device in bytes.
     */
    val deviceDiskFree: Long?
)
