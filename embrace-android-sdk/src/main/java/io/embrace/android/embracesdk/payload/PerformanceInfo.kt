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
    val diskUsage: DiskUsage? = null,

    /**
     * Timestamps where Google ANRs were triggered.
     */
    @Json(name = "ga")
    val googleAnrTimestamps: List<Long>? = null,

    /**
     * ApplicationExitInfo
     */
    @Json(name = "aei")
    val appExitInfoData: List<AppExitInfoData>? = null,

    /**
     * Native thread ANR samples
     */
    @Json(name = "nst")
    val nativeThreadAnrIntervals: List<NativeThreadAnrInterval>? = null,

    /**
     * Network requests that happened during the session
     */
    @Json(name = "nr")
    val networkRequests: NetworkRequests? = null
)
