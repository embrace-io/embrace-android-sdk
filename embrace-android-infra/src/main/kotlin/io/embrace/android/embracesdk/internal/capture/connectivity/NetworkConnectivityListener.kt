package io.embrace.android.embracesdk.internal.capture.connectivity

fun interface NetworkConnectivityListener {

    /**
     * Callback when network status has changed.
     */
    fun onNetworkConnectivityStatusChanged(status: ConnectivityStatus)
}
