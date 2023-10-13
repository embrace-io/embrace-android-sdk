package io.embrace.android.embracesdk.comms.delivery

internal enum class NetworkStatus(val value: String) {
    NOT_REACHABLE("none"),
    WIFI("wifi"),
    WAN("wan"),
    UNKNOWN("unknown")
}
