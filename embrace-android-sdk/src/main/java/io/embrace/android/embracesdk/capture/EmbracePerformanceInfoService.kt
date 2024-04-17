package io.embrace.android.embracesdk.capture

import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.anr.sigquit.GoogleAnrTimestampRepository
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.capture.monitor.ResponsivenessMonitorService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService
import io.embrace.android.embracesdk.payload.NetworkRequests
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.session.captureDataSafely

internal class EmbracePerformanceInfoService(
    private val networkConnectivityService: NetworkConnectivityService,
    private val networkLoggingService: NetworkLoggingService,
    private val metadataService: MetadataService,
    private val googleAnrTimestampRepository: GoogleAnrTimestampRepository,
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
            networkRequests = captureDataSafely(logger) { NetworkRequests(networkLoggingService.getNetworkCallsSnapshot()) },
            googleAnrTimestamps = captureDataSafely(logger) {
                googleAnrTimestampRepository.getGoogleAnrTimestamps(
                    sessionStart,
                    sessionLastKnownTime
                ).toList()
            },
            nativeThreadAnrIntervals = captureDataSafely(logger) {
                nativeThreadSamplerService?.getCapturedIntervals(
                    receivedTermination
                )
            },
            responsivenessMonitorSnapshots = captureDataSafely(logger) { responsivenessMonitorService?.getCapturedData() }
        )
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
            }
        )
    }
}
