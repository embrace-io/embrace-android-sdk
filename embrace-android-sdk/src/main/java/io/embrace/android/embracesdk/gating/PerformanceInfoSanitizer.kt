package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_ANR
import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_CONNECTIVITY
import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_CURRENT_DISK_USAGE
import io.embrace.android.embracesdk.payload.PerformanceInfo

internal class PerformanceInfoSanitizer(
    private val info: PerformanceInfo?,
    private val enabledComponents: Set<String>
) :
    Sanitizable<PerformanceInfo> {
    override fun sanitize(): PerformanceInfo? {
        return info?.copy(
            anrIntervals = anrIntervals(info),
            networkInterfaceIntervals = networkInterfaceIntervals(info),
            diskUsage = diskUsage(info),
            networkRequests = networkRequests(info),
            responsivenessMonitorSnapshots = threadMonitorSnapshots(info)
        )
    }

    private fun anrIntervals(performanceInfo: PerformanceInfo) = when {
        shouldSendANRs() -> performanceInfo.anrIntervals
        else -> null
    }

    private fun threadMonitorSnapshots(performanceInfo: PerformanceInfo) = when {
        shouldSendANRs() -> performanceInfo.responsivenessMonitorSnapshots
        else -> null
    }

    private fun networkInterfaceIntervals(performanceInfo: PerformanceInfo) = when {
        shouldSendNetworkConnectivityIntervals() -> performanceInfo.networkInterfaceIntervals
        else -> null
    }

    private fun diskUsage(performanceInfo: PerformanceInfo) = when {
        shouldSendCurrentDiskUsage() -> performanceInfo.diskUsage
        else -> null
    }

    private fun networkRequests(performanceInfo: PerformanceInfo) = when {
        shouldSendCapturedNetwork() -> performanceInfo.networkRequests
        else -> null
    }

    private fun shouldSendANRs() =
        enabledComponents.contains(PERFORMANCE_ANR)

    private fun shouldSendCurrentDiskUsage() =
        enabledComponents.contains(PERFORMANCE_CURRENT_DISK_USAGE)

    private fun shouldSendNetworkConnectivityIntervals() =
        enabledComponents.contains(PERFORMANCE_CONNECTIVITY)

    private fun shouldSendCapturedNetwork() =
        enabledComponents.contains(SessionGatingKeys.PERFORMANCE_NETWORK)
}
