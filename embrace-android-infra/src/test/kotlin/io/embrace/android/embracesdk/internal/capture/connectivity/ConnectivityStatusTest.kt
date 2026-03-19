package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import io.embrace.android.embracesdk.internal.comms.delivery.toConnectivityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class ConnectivityStatusTest {

    @Test
    fun `Wifi maps to NetworkStatus WIFI`() {
        setOf(true, false).forEach {
            assertEquals(NetworkStatus.WIFI, ConnectivityStatus.Wifi(it).toNetworkStatus())
        }
    }

    @Test
    fun `Wan connected maps to NetworkStatus WAN`() {
        setOf(true, false).forEach {
            assertEquals(NetworkStatus.WAN, ConnectivityStatus.Wan(it).toNetworkStatus())
        }
    }

    @Test
    fun `Unknown maps to NetworkStatus UNKNOWN`() {
        setOf(true, false).forEach {
            assertEquals(NetworkStatus.UNKNOWN, ConnectivityStatus.Unknown(it).toNetworkStatus())
        }
    }

    @Test
    fun `None maps to NetworkStatus NOT_REACHABLE`() {
        assertEquals(NetworkStatus.NOT_REACHABLE, ConnectivityStatus.None.toNetworkStatus())
    }

    @Test
    fun `check equality`() {
        assertEquals(ConnectivityStatus.Wifi(true), ConnectivityStatus.Wifi(true))
        assertEquals(ConnectivityStatus.Wifi(false), ConnectivityStatus.Wifi(false))
        assertEquals(ConnectivityStatus.Wan(true), ConnectivityStatus.Wan(true))
        assertEquals(ConnectivityStatus.Wan(false), ConnectivityStatus.Wan(false))
        assertEquals(ConnectivityStatus.Unknown(true), ConnectivityStatus.Unknown(true))
        assertEquals(ConnectivityStatus.Unknown(false), ConnectivityStatus.Unknown(false))
    }

    @Test
    fun `check inequality`() {
        assertNotEquals(ConnectivityStatus.Wifi(true), ConnectivityStatus.Wifi(false))
        assertNotEquals(ConnectivityStatus.Wan(true), ConnectivityStatus.Wan(false))
        assertNotEquals(ConnectivityStatus.Unknown(true), ConnectivityStatus.Unknown(false))
    }

    @Test
    fun `toConnectivityStatus maps every NetworkStatus to the correct ConnectivityStatus`() {
        assertEquals(ConnectivityStatus.None, NetworkStatus.NOT_REACHABLE.toConnectivityStatus())
        assertEquals(ConnectivityStatus.Wifi(true), NetworkStatus.WIFI.toConnectivityStatus())
        assertEquals(ConnectivityStatus.Wan(true), NetworkStatus.WAN.toConnectivityStatus())
        assertEquals(ConnectivityStatus.Unknown(true), NetworkStatus.UNKNOWN.toConnectivityStatus())
    }
}
