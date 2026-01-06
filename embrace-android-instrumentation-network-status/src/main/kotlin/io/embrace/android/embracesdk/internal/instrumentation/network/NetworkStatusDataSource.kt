package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus

class NetworkStatusDataSource(
    args: InstrumentationArgs,
) : NetworkConnectivityListener, DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_NETWORK_STATE_TRANSITIONS },
    instrumentationName = "network_status_data_source"
) {
    private var span: SpanToken? = null

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        // close previous span
        val timestamp = clock.now()
        span?.stop(endTimeMs = timestamp)
        // start a new span with the new network status
        captureTelemetry {
            span = startSpanCapture(SchemaType.NetworkStatus(status.value), timestamp)
        }
    }
}
