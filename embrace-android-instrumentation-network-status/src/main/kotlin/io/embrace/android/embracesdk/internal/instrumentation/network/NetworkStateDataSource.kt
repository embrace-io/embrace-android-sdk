package io.embrace.android.embracesdk.internal.instrumentation.network

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NetworkState
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NetworkState.Status
import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityListener

class NetworkStateDataSource(
    args: InstrumentationArgs,
) : NetworkConnectivityListener, StateDataSource<Status>(
    args = args,
    stateTypeFactory = ::NetworkState,
    defaultValue = Status.UNKNOWN,
    maxTransitions = MAX_CAPTURED_NETWORK_STATE_TRANSITIONS,
) {
    override fun onNetworkConnectivityStatusChanged(status: ConnectivityStatus) {
        onStateChange(clock.now(), status.toState())
    }

    private fun ConnectivityStatus.toState(): Status =
        when (this) {
            ConnectivityStatus.None -> Status.NOT_REACHABLE
            ConnectivityStatus.Unverified -> Status.UNVERIFIED
            is ConnectivityStatus.Unknown -> {
                if (isConnected) {
                    Status.UNKNOWN
                } else {
                    Status.UNKNOWN_CONNECTING
                }
            }
            is ConnectivityStatus.Wan -> {
                if (isConnected) {
                    Status.WAN
                } else {
                    Status.WAN_CONNECTING
                }
            }
            is ConnectivityStatus.Wifi -> {
                if (isConnected) {
                    Status.WIFI
                } else {
                    Status.WIFI_CONNECTING
                }
            }
        }
}

internal const val MAX_CAPTURED_NETWORK_STATE_TRANSITIONS = 100
