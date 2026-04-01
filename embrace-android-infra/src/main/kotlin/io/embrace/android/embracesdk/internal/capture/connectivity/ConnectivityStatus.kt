package io.embrace.android.embracesdk.internal.capture.connectivity

sealed class ConnectivityStatus(
    val connectionType: ConnectionType,
) {
    class Wifi(override val isConnected: Boolean) : ConnectivityStatus(ConnectionType.WIFI)

    class Wan(override val isConnected: Boolean) : ConnectivityStatus(ConnectionType.WAN)

    class Unknown(override val isConnected: Boolean) : ConnectivityStatus(ConnectionType.UNKNOWN)

    object Unverified : ConnectivityStatus(ConnectionType.UNKNOWN) {
        override val isConnected: Boolean = true
    }

    object None : ConnectivityStatus(ConnectionType.NONE) {
        override val isConnected: Boolean = false
    }

    abstract val isConnected: Boolean

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConnectivityStatus

        if (connectionType != other.connectionType) return false
        if (isConnected != other.isConnected) return false

        return true
    }

    override fun hashCode(): Int {
        var result = connectionType.hashCode()
        result = 31 * result + isConnected.hashCode()
        return result
    }
}

val OptimisticWifi = ConnectivityStatus.Wifi(true)
val OptimisticWan = ConnectivityStatus.Wan(true)
val OptimisticUnknown = ConnectivityStatus.Unknown(true)
