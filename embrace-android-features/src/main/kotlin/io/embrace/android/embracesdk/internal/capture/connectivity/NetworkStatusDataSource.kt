package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.arch.datasource.NoInputValidation
import io.embrace.android.embracesdk.internal.arch.datasource.SpanDataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.startSpanCapture
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.spans.EmbraceSpan

class NetworkStatusDataSource(
    private val clock: Clock,
    spanService: SpanService,
    logger: EmbLogger
) : NetworkConnectivityListener, SpanDataSourceImpl(
    destination = spanService,
    logger = logger,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_NETWORK_STATUS }
) {
    private companion object {
        private const val MAX_CAPTURED_NETWORK_STATUS = 100
    }

    private var span: EmbraceSpan? = null

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        // close previous span
        val timestamp = clock.now()
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
            startSpanCapture(SchemaType.NetworkStatus(status), timestamp).apply {
                span = this
            }
        }
    }
}
