package io.embrace.android.embracesdk.internal.capture.connectivity

/**
 * Type that an app's network connection is classified into.
 */
enum class ConnectionType(
    val openForDelivery: Boolean,
    val typeName: String,
) {
    WIFI(openForDelivery = true, typeName = "wifi"),
    WAN(openForDelivery = true, typeName = "wan"),
    UNKNOWN(openForDelivery = true, typeName = "unknown"),
    NONE(openForDelivery = false, typeName = "none"),
}
