package io.embrace.android.embracesdk.capture

import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.anr.sigquit.GoogleAnrTimestampRepository
import io.embrace.android.embracesdk.capture.aei.ApplicationExitInfoService
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.monitor.ResponsivenessMonitorService
import io.embrace.android.embracesdk.capture.powersave.PowerSaveModeService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
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
    private val metadataService: MetadataService,
    private val googleAnrTimestampRepository: GoogleAnrTimestampRepository,
    private val applicationExitInfoService: ApplicationExitInfoService?,
    private val nativeThreadSamplerService: NativeThreadSamplerService?,
    private val responsivenessMonitorService: ResponsivenessMonitorService?,
    private val logger: InternalEmbraceLogger
) : PerformanceInfoService {

    override fun getSessionPerformanceInfo(
        sessionStart: Long,
        sessionLastKnownTime: Long,
        coldStart: Boolean,
        receivedTermination: Boolean?
    ): PerformanceInfo {
        val info = getPerformanceInfo(sessionStart, sessionLastKnownTime, coldStart)

        return info.copy(
            appExitInfoData = captureDataSafely(logger) {
                captureAppExitInfoData(coldStart, applicationExitInfoService)
            },
            networkRequests = captureDataSafely(logger) { NetworkRequests(networkLoggingService.getNetworkCallsSnapshot()) },
            anrIntervals = captureDataSafely(logger) { anrService?.getCapturedData()?.toList() },
            googleAnrTimestamps = captureDataSafely(logger) {
                googleAnrTimestampRepository.getGoogleAnrTimestamps(
                    sessionStart,
                    sessionLastKnownTime
                ).toList()
            },
            powerSaveModeIntervals = captureDataSafely(logger) {
                powerSaveModeService.getCapturedData()?.toList()
            },
            nativeThreadAnrIntervals = captureDataSafely(logger) {
                nativeThreadSamplerService?.getCapturedIntervals(
                    receivedTermination
                )
            },
            responsivenessMonitorSnapshots = captureDataSafely(logger) { responsivenessMonitorService?.getCapturedData() }
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
        return PerformanceInfo(
            diskUsage = captureDataSafely(logger) { metadataService.getDiskUsage()?.copy() },
            networkInterfaceIntervals = captureDataSafely(logger) {
                networkConnectivityService.getCapturedData()?.toList()
            },
            powerSaveModeIntervals = captureDataSafely(logger) {
                powerSaveModeService.getCapturedData()?.toList()
            },
        )
    }
}
