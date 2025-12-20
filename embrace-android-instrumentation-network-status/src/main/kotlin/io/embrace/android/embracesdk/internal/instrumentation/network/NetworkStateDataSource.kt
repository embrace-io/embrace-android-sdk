package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NetworkState
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NetworkState.Status
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener
import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus

class NetworkStateDataSource(
    args: InstrumentationArgs,
) : NetworkConnectivityListener, StateDataSource<Status>(
    args = args,
    stateTypeFactory = ::NetworkState,
    defaultValue = Status.UNKNOWN,
    maxTransitions = MAX_CAPTURED_NETWORK_STATE_TRANSITIONS
) {
    override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        onStateChange(clock.now(), status.toState())
    }

    private fun NetworkStatus.toState(): Status =
        when (this) {
            NetworkStatus.NOT_REACHABLE -> Status.NOT_REACHABLE
            NetworkStatus.WIFI -> Status.WIFI
            NetworkStatus.WAN -> Status.WAN
            NetworkStatus.UNKNOWN -> Status.WAN
        }
}

internal const val MAX_CAPTURED_NETWORK_STATE_TRANSITIONS = 100
