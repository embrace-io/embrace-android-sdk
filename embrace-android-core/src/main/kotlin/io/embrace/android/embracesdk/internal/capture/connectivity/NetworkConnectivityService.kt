package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
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
     * Returns the current NetworkStatus.
     */
    fun getCurrentNetworkStatus(): NetworkStatus

    /**
     * Calculate the device's IP address
     */
    val ipAddress: String?

    /**
     * Start listening for network connectivity changes
     */
    fun register()
}
