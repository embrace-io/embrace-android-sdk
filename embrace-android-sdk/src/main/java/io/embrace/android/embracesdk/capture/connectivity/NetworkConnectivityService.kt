package io.embrace.android.embracesdk.capture.connectivity

import io.embrace.android.embracesdk.comms.delivery.NetworkStatus
import java.io.Closeable

/**
 * Detects and records which network the device is connected to.
 */
internal interface NetworkConnectivityService : Closeable {

    /**
     * Record the connection type at the start of the session and open a connectivity interval with it,
     * with a start time that matches the session start time.
     *
     * @param startTime of the session
     */
    fun networkStatusOnSessionStarted(startTime: Long)

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
