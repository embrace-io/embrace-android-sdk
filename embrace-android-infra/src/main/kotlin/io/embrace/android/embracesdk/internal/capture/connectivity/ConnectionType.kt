package io.embrace.android.embracesdk.internal.capture.connectivity

/**
 * Type that an app's network connection is classified into.
 */
enum class ConnectionType(
    val openForDelivery: Boolean,
) {
    WIFI(openForDelivery = true),
    WAN(openForDelivery = true),
    UNKNOWN(openForDelivery = true),
    NONE(openForDelivery = false),
}
