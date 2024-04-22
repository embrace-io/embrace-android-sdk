package io.embrace.android.embracesdk.capture

import io.embrace.android.embracesdk.capture.metadata.MetadataService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.payload.PerformanceInfo
import io.embrace.android.embracesdk.session.captureDataSafely

internal class EmbracePerformanceInfoService(
    private val metadataService: MetadataService,
    private val logger: InternalEmbraceLogger
) : PerformanceInfoService {

    override fun getSessionPerformanceInfo(
        sessionStart: Long,
        sessionLastKnownTime: Long,
        coldStart: Boolean,
        receivedTermination: Boolean?
    ): PerformanceInfo {
        return getPerformanceInfo(sessionStart, sessionLastKnownTime, coldStart)
    }

    override fun getPerformanceInfo(
        startTime: Long,
        endTime: Long,
        coldStart: Boolean
    ): PerformanceInfo {
        return PerformanceInfo(
            diskUsage = captureDataSafely(logger) { metadataService.getDiskUsage()?.copy() }
        )
    }
}
