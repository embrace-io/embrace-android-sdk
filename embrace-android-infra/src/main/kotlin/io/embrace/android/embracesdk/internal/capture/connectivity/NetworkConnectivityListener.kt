package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus

interface NetworkConnectivityListener {

    /**
     * Legacy callback when the network status has changed.
     */
    fun onNetworkConnectivityStatusChanged(status: NetworkStatus)

    /**
     * Callback when network status has changed.
     */
    fun onNetworkConnectivityStatusChanged(status: ConnectivityStatus) {
        onNetworkConnectivityStatusChanged(status.toNetworkStatus())
    }
}
