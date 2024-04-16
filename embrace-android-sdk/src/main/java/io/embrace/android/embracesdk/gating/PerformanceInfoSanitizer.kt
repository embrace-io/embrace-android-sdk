package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_ANR
import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_CONNECTIVITY
import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_CURRENT_DISK_USAGE
import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_LOW_MEMORY
import io.embrace.android.embracesdk.payload.PerformanceInfo

internal class PerformanceInfoSanitizer(
    private val info: PerformanceInfo?,
    private val enabledComponents: Set<String>
) :
    Sanitizable<PerformanceInfo> {
    override fun sanitize(): PerformanceInfo? {
        return info?.copy(
            networkInterfaceIntervals = networkInterfaceIntervals(info),
            memoryWarnings = memoryWarnings(info),
            diskUsage = diskUsage(info),
            networkRequests = networkRequests(info),
            responsivenessMonitorSnapshots = threadMonitorSnapshots(info)
        )
    }

    private fun threadMonitorSnapshots(performanceInfo: PerformanceInfo) = when {
        shouldSendANRs() -> performanceInfo.responsivenessMonitorSnapshots
        else -> null
    }

    private fun networkInterfaceIntervals(performanceInfo: PerformanceInfo) = when {
        shouldSendNetworkConnectivityIntervals() -> performanceInfo.networkInterfaceIntervals
        else -> null
    }

    private fun memoryWarnings(performanceInfo: PerformanceInfo) = when {
        shouldSendLowMemoryWarnings() -> performanceInfo.memoryWarnings
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

    private fun shouldSendLowMemoryWarnings() =
        enabledComponents.contains(PERFORMANCE_LOW_MEMORY)

    private fun shouldSendCapturedNetwork() =
        enabledComponents.contains(SessionGatingKeys.PERFORMANCE_NETWORK)
}
