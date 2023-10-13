package io.embrace.android.embracesdk.capture.connectivity

import io.embrace.android.embracesdk.comms.delivery.NetworkStatus

internal interface NetworkConnectivityListener {

    /**
     * Called when the network status has changed.
     */
    fun onNetworkConnectivityStatusChanged(status: NetworkStatus)
}
