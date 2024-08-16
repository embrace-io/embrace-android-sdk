package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import java.io.Closeable

/**
 * Detects and records which network the device is connected to.
 */
public interface NetworkConnectivityService : Closeable {

    /**
     * Record the connection type at the start of the session and open a connectivity interval with it,
     * with a start time that matches the session start time.
     *
     * @param startTime of the session
     */
    public fun networkStatusOnSessionStarted(startTime: Long)

    /**
     * Adds a listener for changes in the connectivity status.
     */
    public fun addNetworkConnectivityListener(listener: NetworkConnectivityListener)

    /**
     * Removes a listener for changes in the connectivity status.
     */
    public fun removeNetworkConnectivityListener(listener: NetworkConnectivityListener)

    /**
     * Returns the current NetworkStatus.
     */
    public fun getCurrentNetworkStatus(): NetworkStatus

    /**
     * Calculate the device's IP address
     */
    public val ipAddress: String?

    /**
     * Start listening for network connectivity changes
     */
    public fun register()
}
