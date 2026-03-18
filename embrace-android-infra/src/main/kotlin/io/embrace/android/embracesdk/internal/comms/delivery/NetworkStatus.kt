package io.embrace.android.embracesdk.internal.comms.delivery

import io.embrace.android.embracesdk.internal.capture.connectivity.ConnectivityStatus

enum class NetworkStatus(val value: String) {
    NOT_REACHABLE("none"),
    WIFI("wifi"),
    WAN("wan"),
    UNKNOWN("unknown");

    /**
     * Returns true if the device is connected to a network.
     * [UNKNOWN] is considered reachable so we attempt to send network calls.
     */
    val isReachable: Boolean
        get() = this != NOT_REACHABLE
}

fun NetworkStatus.toConnectivityStatus(): ConnectivityStatus =
    when (this) {
        NetworkStatus.NOT_REACHABLE -> ConnectivityStatus.None
        NetworkStatus.WIFI -> OptimisticWifi
        NetworkStatus.WAN -> OptimisticWan
        NetworkStatus.UNKNOWN -> OptimisticUnknown
    }

private val OptimisticWifi = ConnectivityStatus.Wifi(true)
private val OptimisticWan = ConnectivityStatus.Wan(true)
private val OptimisticUnknown = ConnectivityStatus.Unknown(true)
