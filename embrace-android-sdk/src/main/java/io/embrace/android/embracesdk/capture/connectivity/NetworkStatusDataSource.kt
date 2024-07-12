package io.embrace.android.embracesdk.capture.connectivity

import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.spans.EmbraceSpan

internal class NetworkStatusDataSource(
    spanService: SpanService,
    logger: EmbLogger
) : SpanDataSourceImpl(
    destination = spanService,
    logger = logger,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_NETWORK_STATUS }
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
            startSpanCapture(SchemaType.NetworkStatus(networkStatus), timestamp).apply {
                span = this
            }
        }
    }
}
