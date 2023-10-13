package io.embrace.android.embracesdk.capture

import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.anr.sigquit.GoogleAnrTimestampRepository
import io.embrace.android.embracesdk.capture.aei.ApplicationExitInfoService
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.memory.MemoryService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.powersave.PowerSaveModeService
import io.embrace.android.embracesdk.capture.strictmode.StrictModeService
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.payload.NetworkRequests
import io.embrace.android.embracesdk.payload.PerformanceInfo

internal class EmbracePerformanceInfoService(
    private val anrService: AnrService?,
    private val networkConnectivityService: NetworkConnectivityService,
    private val networkLoggingService: NetworkLoggingService,
    private val powerSaveModeService: PowerSaveModeService,
    private val memoryService: MemoryService,
    private val metadataService: MetadataService,
    private val googleAnrTimestampRepository: GoogleAnrTimestampRepository,
    private val applicationExitInfoService: ApplicationExitInfoService?,
    private val strictModeService: StrictModeService,
    private val nativeThreadSamplerService: NativeThreadSamplerService?
) : PerformanceInfoService {

    override fun getSessionPerformanceInfo(
        sessionStart: Long,
        sessionLastKnownTime: Long,
        coldStart: Boolean,
        receivedTermination: Boolean?
    ): PerformanceInfo {
        logDeveloper(
            "EmbracePerformanceInfoService",
            "Session performance info start time: $sessionStart"
        )
        val requests = NetworkRequests(
            networkLoggingService.getNetworkCallsForSession(
                sessionStart,
                sessionLastKnownTime
            )
        )
        val info = getPerformanceInfo(sessionStart, sessionLastKnownTime, coldStart)

        return info.copy(
            appExitInfoData = when {
                applicationExitInfoService != null &&
                    coldStart -> ArrayList(applicationExitInfoService.getCapturedData())
                else -> null
            },
            networkRequests = requests,
            anrIntervals = anrService?.getCapturedData()?.toList(),
            anrProcessErrors = anrService?.getAnrProcessErrors(sessionStart)?.toList(),
            googleAnrTimestamps = googleAnrTimestampRepository.getGoogleAnrTimestamps(
                sessionStart,
                sessionLastKnownTime
            ).toList(),
            powerSaveModeIntervals = powerSaveModeService.getCapturedData()?.toList(),
            strictmodeViolations = strictModeService.getCapturedData()?.toList(),
            nativeThreadAnrIntervals = nativeThreadSamplerService?.getCapturedIntervals(
                receivedTermination
            )
        )
    }

    override fun getPerformanceInfo(
        startTime: Long,
        endTime: Long,
        coldStart: Boolean
    ): PerformanceInfo {
        logDeveloper("EmbracePerformanceInfoService", "Building performance info")

        return PerformanceInfo(
            diskUsage = metadataService.getDiskUsage()?.copy(),
            memoryWarnings = memoryService.getCapturedData()?.toList(),
            networkInterfaceIntervals = networkConnectivityService.getCapturedData()?.toList(),
            powerSaveModeIntervals = powerSaveModeService.getCapturedData()?.toList(),
        )
    }
}
