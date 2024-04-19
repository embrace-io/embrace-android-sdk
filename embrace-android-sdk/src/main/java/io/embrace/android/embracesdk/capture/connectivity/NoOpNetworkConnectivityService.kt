package io.embrace.android.embracesdk.capture.connectivity

import io.embrace.android.embracesdk.comms.delivery.NetworkStatus

internal class NoOpNetworkConnectivityService : NetworkConnectivityService {
    override fun close() {}

    override fun networkStatusOnSessionStarted(startTime: Long) {}

    override fun addNetworkConnectivityListener(listener: NetworkConnectivityListener) {}

    override fun removeNetworkConnectivityListener(listener: NetworkConnectivityListener) {}

    override fun getCurrentNetworkStatus(): NetworkStatus {
        return NetworkStatus.UNKNOWN
    }

    override val ipAddress: String? = null
}
