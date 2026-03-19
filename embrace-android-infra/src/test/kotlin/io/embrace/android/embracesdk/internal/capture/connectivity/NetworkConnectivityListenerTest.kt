package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import org.junit.Assert.assertEquals
import org.junit.Test

internal class NetworkConnectivityListenerTest {

    @Test
    fun `default implementation of new interface fires old interface with appropriate value`() {
        val mapping = mapOf(
            ConnectivityStatus.Wifi(true) to NetworkStatus.WIFI,
            ConnectivityStatus.Wifi(false) to NetworkStatus.WIFI,
            ConnectivityStatus.Wan(true) to NetworkStatus.WAN,
            ConnectivityStatus.Wan(false) to NetworkStatus.WAN,
            ConnectivityStatus.Unknown(true) to NetworkStatus.UNKNOWN,
            ConnectivityStatus.Unknown(false) to NetworkStatus.UNKNOWN,
            ConnectivityStatus.Unverified to NetworkStatus.UNKNOWN,
            ConnectivityStatus.None to NetworkStatus.NOT_REACHABLE
        )
        var receivedNetworkStatus: NetworkStatus? = null
        val listener = object : NetworkConnectivityListener {
            override fun onNetworkConnectivityStatusChanged(status: NetworkStatus) {
                receivedNetworkStatus = status
            }
        }

        mapping.forEach {
            listener.onNetworkConnectivityStatusChanged(it.key)
            assertEquals(it.value, receivedNetworkStatus)
        }
    }
}
