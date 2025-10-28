package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.logging.EmbLogger

class NetworkStatusDataSource(
    private val clock: Clock,
    destination: TelemetryDestination,
    logger: EmbLogger,
) : NetworkConnectivityListener, DataSourceImpl(
    destination = destination,
    logger = logger,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_NETWORK_STATUS }
) {
    private companion object {
        private const val MAX_CAPTURED_NETWORK_STATUS = 100
    }

    private var span: SpanToken? = null

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        // close previous span
        val timestamp = clock.now()
        if (span != null) {
            span?.stop(endTimeMs = timestamp)
        }
        // start a new span with the new network status
        captureTelemetry {
            span = startSpanCapture(SchemaType.NetworkStatus(status.value), timestamp)
        }
    }
}
