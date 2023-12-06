package io.embrace.android.embracesdk.payload

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.embrace.android.embracesdk.anr.detection.AnrProcessErrorStateInfo

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
     * Occasions where the device reported that the memory is running low.
     */
    @Json(name = "mw")
    val memoryWarnings: List<MemoryWarning>? = null,

    /**
     * Periods during which the device was connected to WIFI, WAN, or no network.
     */
    @Json(name = "ns")
    val networkInterfaceIntervals: List<Interval>? = null,

    /**
     * Periods during which the application was not responding (UI thread blocked for > 1 sec).
     */
    @Json(name = "anr")
    val anrIntervals: List<AnrInterval>? = null,

    /**
     * Periods during which the application was not responding (UI thread blocked for > 1 sec),
     * detected by the OS, not Embrace. This is what we call ANR Process Errors.
     */
    @Json(name = "anr_pe")
    val anrProcessErrors: List<AnrProcessErrorStateInfo>? = null,

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
     * Periods of save power mode
     * lp refers "low power"
     */
    @Json(name = "lp")
    val powerSaveModeIntervals: List<PowerModeInterval>? = null,

    /**
     * Network requests that happened during the session
     */
    @Json(name = "nr")
    val networkRequests: NetworkRequests? = null,

    /**
     * StrictMode violations captured during the session
     */
    @Json(name = "v")
    val strictmodeViolations: List<StrictModeViolation>? = null
)
