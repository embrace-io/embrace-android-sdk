package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.SpanToken
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectionType
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.comms.delivery.toConnectivityStatus
import java.util.concurrent.atomic.AtomicReference

class NetworkStatusDataSource(
    args: InstrumentationArgs,
) : NetworkConnectivityListener, DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { MAX_CAPTURED_NETWORK_STATE_TRANSITIONS },
    instrumentationName = "network_status_data_source"
) {
    private val currentConnectionType: AtomicReference<ConnectionType> = AtomicReference(null)
    private var span: SpanToken? = null

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        onNetworkConnectivityStatusChanged(status.toConnectivityStatus())
    }

    override fun onNetworkConnectivityStatusChanged(status: ConnectivityStatus) {
        val newConnectionType = status.connectionType
        if (currentConnectionType.getAndSet(newConnectionType) != newConnectionType) {
            // close previous span
            val timestamp = clock.now()
            span?.stop(endTimeMs = timestamp)
            // start a new span with the new connect type
            captureTelemetry {
                span = startSpanCapture(SchemaType.NetworkStatus(newConnectionType.typeName), timestamp)
            }
        }
    }
}
