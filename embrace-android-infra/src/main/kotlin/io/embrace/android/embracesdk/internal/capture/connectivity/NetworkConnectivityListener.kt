package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.comms.delivery.toConnectivityStatus

interface NetworkConnectivityListener {

    /**
     * Legacy callback when the network status has changed.
     */
    fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
        onNetworkConnectivityStatusChanged(status.toConnectivityStatus())
    }

    /**
     * Callback when network status has changed.
     */
    fun onNetworkConnectivityStatusChanged(status: ConnectivityStatus)
}
