package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.datasource.StateToken
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NetworkState.Status
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener

class NetworkStateDataSource(
    args: InstrumentationArgs,
) : NetworkConnectivityListener, MemoryCleanerListener, DataSourceImpl(
    args = args,
    limitStrategy = NoopLimitStrategy
) {
    private var currentState: Status = Status.UNKNOWN
    private var stateToken: StateToken? = null

    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        val timestamp = clock.now()
        val newState = status.toState()
        val currentStateToken = stateToken
        if (currentStateToken == null) {
            stateToken = newStateSpan(newState)
        } else if (newState != currentState) {
            captureTelemetry {
                currentStateToken.update(
                    timestampMs = timestamp,
                    newValue = newState.value
                )
            }
            currentState = newState
        }
    }

    override fun resetDataCaptureLimits() {
        stateToken = newStateSpan(currentState)
    }

    override fun cleanCollections() {
        stateToken?.end(clock.now())
    }

    private fun newStateSpan(initialStatus: Status): StateToken? =
        captureTelemetry {
            createState(
                state = SchemaType.NetworkState(initialStatus),
                initialValue = initialStatus.value
            )
        }

    private fun NetworkStatus.toState(): Status =
        when (this) {
            NetworkStatus.NOT_REACHABLE -> Status.NOT_REACHABLE
            NetworkStatus.WIFI -> Status.WIFI
            NetworkStatus.WAN -> Status.WAN
            NetworkStatus.UNKNOWN -> Status.WAN
        }
}
