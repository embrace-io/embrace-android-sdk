package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus

public fun interface NetworkConnectivityListener {

    /**
     * Called when the network status has changed.
     */
    public fun onNetworkConnectivityStatusChanged(status: NetworkStatus)
}
