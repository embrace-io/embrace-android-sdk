package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.anr.detection.AnrProcessErrorStateInfo

/**
 * Describes information about how the device is performing.
 */
internal data class PerformanceInfo(

    /**
     * Current disk space usage of the app, and free space on the device.
     */
    @SerializedName("ds")
    val diskUsage: DiskUsage? = null,

    /**
     * Occasions where the device reported that the memory is running low.
     */
    @SerializedName("mw")
    val memoryWarnings: List<MemoryWarning>? = null,

    /**
     * Periods during which the device was connected to WIFI, WAN, or no network.
     */
    @SerializedName("ns")
    val networkInterfaceIntervals: List<Interval>? = null,

    /**
     * Periods during which the application was not responding (UI thread blocked for > 1 sec).
     */
    @SerializedName("anr")
    val anrIntervals: List<AnrInterval>? = null,

    /**
     * Periods during which the application was not responding (UI thread blocked for > 1 sec),
     * detected by the OS, not Embrace. This is what we call ANR Process Errors.
     */
    @SerializedName("anr_pe")
    val anrProcessErrors: List<AnrProcessErrorStateInfo>? = null,

    /**
     * Timestamps where Google ANRs were triggered.
     */
    @SerializedName("ga")
    val googleAnrTimestamps: List<Long>? = null,

    /**
     * ApplicationExitInfo
     */
    @SerializedName("aei")
    val appExitInfoData: List<AppExitInfoData>? = null,

    /**
     * Native thread ANR samples
     */
    @SerializedName("nst")
    val nativeThreadAnrIntervals: List<NativeThreadAnrInterval>? = null,

    /**
     * Periods of save power mode
     * lp refers "low power"
     */
    @SerializedName("lp")
    val powerSaveModeIntervals: List<PowerModeInterval>? = null,

    /**
     * Network requests that happened during the session
     */
    @SerializedName("nr")
    val networkRequests: NetworkRequests? = null,

    /**
     * Stats about the responsiveness of the ANR monitoring components
     */
    @SerializedName("rms")
    val responsivenessMonitorSnapshots: List<ResponsivenessSnapshot>? = null
)
