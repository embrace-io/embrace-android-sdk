package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.payload.DiskUsage
import io.embrace.android.embracesdk.payload.NetworkRequests
import io.embrace.android.embracesdk.payload.NetworkSessionV2
import io.embrace.android.embracesdk.payload.PerformanceInfo

internal fun fakePerformanceInfo() = PerformanceInfo(
    anrIntervals = emptyList(),
    networkInterfaceIntervals = emptyList(),
    memoryWarnings = emptyList(),
    diskUsage = DiskUsage(0, 0),
    networkRequests = NetworkRequests(NetworkSessionV2(emptyList(), emptyMap()))
)
