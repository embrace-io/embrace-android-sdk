package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus

fun interface NetworkConnectivityListener {

    /**
     * Called when the network status has changed.
     */
    fun onNetworkConnectivityStatusChanged(status: NetworkStatus)
}
