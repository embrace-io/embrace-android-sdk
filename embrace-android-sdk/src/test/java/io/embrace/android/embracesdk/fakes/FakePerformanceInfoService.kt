package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.payload.PerformanceInfo

/**
 * Fake [PerformanceInfoService] that allows you to set whatever you want on it to be returned
 */
internal class FakePerformanceInfoService(
    var performanceInfo: PerformanceInfo = PerformanceInfo(),
    var sessionPerformanceInfo: PerformanceInfo = PerformanceInfo()
) : PerformanceInfoService {
    override fun getPerformanceInfo(
        startTime: Long,
        endTime: Long,
        coldStart: Boolean
    ): PerformanceInfo = performanceInfo

    override fun getSessionPerformanceInfo(
        sessionStart: Long,
        sessionLastKnownTime: Long,
        coldStart: Boolean,
        receivedTermination: Boolean?
    ): PerformanceInfo =
        sessionPerformanceInfo
}
