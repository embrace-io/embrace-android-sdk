package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus

/**
 * Type that an app's network connection is classified into.
 */
enum class ConnectionType(
    val openForDelivery: Boolean,
    val typeName: String
) {
    WIFI(openForDelivery = true, typeName = NetworkStatus.WIFI.value),
    WAN(openForDelivery = true, typeName = NetworkStatus.WAN.value),
    UNKNOWN(openForDelivery = true, typeName = NetworkStatus.UNKNOWN.value),
    NONE(openForDelivery = false, typeName = NetworkStatus.NOT_REACHABLE.value),
}
