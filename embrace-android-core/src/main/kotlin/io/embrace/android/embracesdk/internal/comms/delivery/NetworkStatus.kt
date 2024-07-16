package io.embrace.android.embracesdk.internal.comms.delivery

public enum class NetworkStatus(public val value: String) {
    NOT_REACHABLE("none"),
    WIFI("wifi"),
    WAN("wan"),
    UNKNOWN("unknown");

    /**
     * Returns true if the device is connected to a network.
     * [UNKNOWN] is considered reachable so we attempt to send network calls.
     */
    public val isReachable: Boolean
        get() = this != NOT_REACHABLE
}
