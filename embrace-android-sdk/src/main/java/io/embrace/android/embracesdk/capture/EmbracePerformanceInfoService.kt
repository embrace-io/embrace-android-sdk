package io.embrace.android.embracesdk.capture

import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.anr.sigquit.GoogleAnrTimestampRepository
import io.embrace.android.embracesdk.capture.aei.ApplicationExitInfoService
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.memory.MemoryService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.monitor.ResponsivenessMonitorService
import io.embrace.android.embracesdk.capture.powersave.PowerSaveModeService
import io.embrace.android.embracesdk.capture.strictmode.StrictModeService
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Companion.logDeveloper
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.payload.AppExitInfoData
import io.embrace.android.embracesdk.payload.NetworkRequests
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.session.captureDataSafely

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
    private val nativeThreadSamplerService: NativeThreadSamplerService?,
    private val responsivenessMonitorService: ResponsivenessMonitorService?
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
        val info = getPerformanceInfo(sessionStart, sessionLastKnownTime, coldStart)

        return info.copy(
            appExitInfoData = captureDataSafely {
                captureAppExitInfoData(coldStart, applicationExitInfoService)
            },
            networkRequests = captureDataSafely { NetworkRequests(networkLoggingService.getNetworkCallsSnapshot()) },
            anrIntervals = captureDataSafely { anrService?.getCapturedData()?.toList() },
            anrProcessErrors = captureDataSafely {
                anrService?.getAnrProcessErrors(sessionStart)?.toList()
            },
            googleAnrTimestamps = captureDataSafely {
                googleAnrTimestampRepository.getGoogleAnrTimestamps(
                    sessionStart,
                    sessionLastKnownTime
                ).toList()
            },
            powerSaveModeIntervals = captureDataSafely {
                powerSaveModeService.getCapturedData()?.toList()
            },
            strictmodeViolations = captureDataSafely { strictModeService.getCapturedData()?.toList() },
            nativeThreadAnrIntervals = captureDataSafely {
                nativeThreadSamplerService?.getCapturedIntervals(
                    receivedTermination
                )
            },
            responsivenessMonitorSnapshots = captureDataSafely { responsivenessMonitorService?.getCapturedData() }
        )
    }

    private fun captureAppExitInfoData(
        coldStart: Boolean,
        applicationExitInfoService: ApplicationExitInfoService?
    ): ArrayList<AppExitInfoData>? {
        return when {
            applicationExitInfoService != null &&
                coldStart -> ArrayList(applicationExitInfoService.getCapturedData())

            else -> null
        }
    }

    override fun getPerformanceInfo(
        startTime: Long,
        endTime: Long,
        coldStart: Boolean
    ): PerformanceInfo {
        logDeveloper("EmbracePerformanceInfoService", "Building performance info")

        return PerformanceInfo(
            diskUsage = captureDataSafely { metadataService.getDiskUsage()?.copy() },
            memoryWarnings = captureDataSafely { memoryService.getCapturedData()?.toList() },
            networkInterfaceIntervals = captureDataSafely {
                networkConnectivityService.getCapturedData()?.toList()
            },
            powerSaveModeIntervals = captureDataSafely {
                powerSaveModeService.getCapturedData()?.toList()
            },
        )
    }
}
