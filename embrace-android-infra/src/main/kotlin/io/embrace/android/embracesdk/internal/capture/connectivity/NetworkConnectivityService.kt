package io.embrace.android.embracesdk.internal.capture.connectivity

import java.io.Closeable

/**
 * Detects and records which network the device is connected to.
 */
interface NetworkConnectivityService : Closeable {

    /**
     * Adds a listener for changes in the connectivity status.
     */
    fun addNetworkConnectivityListener(listener: NetworkConnectivityListener)

    /**
     * Removes a listener for changes in the connectivity status.
     */
    fun removeNetworkConnectivityListener(listener: NetworkConnectivityListener)

    /**
     * Start listening for network connectivity changes
     */
    fun register()
}
