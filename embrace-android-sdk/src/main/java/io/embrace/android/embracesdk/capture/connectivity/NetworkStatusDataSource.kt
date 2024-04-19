package io.embrace.android.embracesdk.capture.connectivity

import io.embrace.android.embracesdk.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.arch.destination.StartSpanData
import io.embrace.android.embracesdk.arch.destination.StartSpanMapper
import io.embrace.android.embracesdk.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.arch.schema.SchemaType
import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.spans.EmbraceSpan

internal class NetworkStatusDataSource(
    spanService: SpanService,
    logger: InternalEmbraceLogger
) : StartSpanMapper<NetworkStatusData>, SpanDataSourceImpl(
    destination = spanService,
    logger = logger,
    limitStrategy = UpToLimitStrategy(logger) { MAX_CAPTURED_NETWORK_STATUS }
) {
    private companion object {
        private const val MAX_CAPTURED_NETWORK_STATUS = 100
    }

    private var span: EmbraceSpan? = null

    fun networkStatusChange(networkStatus: NetworkStatus, timestamp: Long) {
        // close previous span
        if (span != null) {
            captureSpanData(
                countsTowardsLimits = false,
                inputValidation = NoInputValidation,
                captureAction = {
                    span?.stop(endTimeMs = timestamp)
                }
            )
        }
        // start a new span with the new network status
        captureSpanData(
            countsTowardsLimits = true,
            inputValidation = NoInputValidation
        ) {
            startSpanCapture(NetworkStatusData(networkStatus, timestamp), ::toStartSpanData)
                .apply {
                    span = this
                }
        }
    }

    override fun resetDataCaptureLimits() {
        super.resetDataCaptureLimits()
    }

    override fun toStartSpanData(obj: NetworkStatusData): StartSpanData {
        return StartSpanData(
            SchemaType.NetworkStatus(obj.networkStatus),
            spanStartTimeMs = obj.timestamp
        )
    }
}

internal data class NetworkStatusData(
    val networkStatus: NetworkStatus,
    val timestamp: Long
)
