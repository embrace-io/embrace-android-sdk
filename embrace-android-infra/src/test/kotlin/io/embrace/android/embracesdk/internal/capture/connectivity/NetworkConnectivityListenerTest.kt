package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import org.junit.Assert.assertEquals
import org.junit.Test

internal class NetworkConnectivityListenerTest {

    @Test
    fun `default implementation of old interface fires new interface with appropriate value`() {
        val mapping = mapOf(
            NetworkStatus.WIFI to ConnectivityStatus.Wifi(true),
            NetworkStatus.WAN to ConnectivityStatus.Wan(true),
            NetworkStatus.UNKNOWN to ConnectivityStatus.Unknown(true),
            NetworkStatus.NOT_REACHABLE to ConnectivityStatus.None
        )
        var receivedStatus: ConnectivityStatus? = null
        val listener = object : NetworkConnectivityListener {
            override fun onNetworkConnectivityStatusChanged(status: ConnectivityStatus) {
                receivedStatus = status
            }
        }

        mapping.forEach {
            listener.onNetworkConnectivityStatusChanged(it.key)
            assertEquals(it.value, receivedStatus)
        }
    }
}
