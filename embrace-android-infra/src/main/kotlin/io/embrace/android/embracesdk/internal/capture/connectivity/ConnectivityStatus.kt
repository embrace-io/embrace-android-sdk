package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus

sealed class ConnectivityStatus(
    val connectionType: ConnectionType,
) {
    data class Wifi(override val isConnected: Boolean) : ConnectivityStatus(ConnectionType.WIFI)

    data class Wan(override val isConnected: Boolean) : ConnectivityStatus(ConnectionType.WAN)

    data class Unknown(override val isConnected: Boolean) : ConnectivityStatus(ConnectionType.UNKNOWN)

    object None : ConnectivityStatus(ConnectionType.NONE) {
        override val isConnected: Boolean = false
    }

    abstract val isConnected: Boolean
}

fun ConnectivityStatus.toNetworkStatus(): NetworkStatus = when (connectionType) {
    ConnectionType.WIFI -> NetworkStatus.WIFI
    ConnectionType.WAN -> NetworkStatus.WAN
    ConnectionType.UNKNOWN -> NetworkStatus.UNKNOWN
    ConnectionType.NONE -> NetworkStatus.NOT_REACHABLE
}
