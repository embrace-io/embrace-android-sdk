package io.embrace.android.embracesdk.gating

import io.embrace.android.embracesdk.gating.SessionGatingKeys.PERFORMANCE_CURRENT_DISK_USAGE
import io.embrace.android.embracesdk.payload.PerformanceInfo

internal class PerformanceInfoSanitizer(
    private val info: PerformanceInfo?,
    private val enabledComponents: Set<String>
) :
    Sanitizable<PerformanceInfo> {
    override fun sanitize(): PerformanceInfo? {
        return info?.copy(
            diskUsage = diskUsage(info)
        )
    }

    private fun diskUsage(performanceInfo: PerformanceInfo) = when {
        shouldSendCurrentDiskUsage() -> performanceInfo.diskUsage
        else -> null
    }

    private fun shouldSendCurrentDiskUsage() =
        enabledComponents.contains(PERFORMANCE_CURRENT_DISK_USAGE)
}
