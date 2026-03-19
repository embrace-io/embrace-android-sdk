package io.embrace.android.embracesdk.internal.capture.connectivity

import io.embrace.android.embracesdk.internal.comms.delivery.NetworkStatus
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ConnectionTypeTest {

    @Test
    fun `verify deliverability status for each connection type`() {
        ConnectionType.entries.forEach { type ->
            assertEquals(type != ConnectionType.NONE, type.openForDelivery)
        }
    }

    @Test
    fun `verify name of every time matches corresponding network status enum value`() {
        assertEquals(NetworkStatus.WIFI.value, ConnectionType.WIFI.typeName)
        assertEquals(NetworkStatus.WAN.value, ConnectionType.WAN.typeName)
        assertEquals(NetworkStatus.UNKNOWN.value, ConnectionType.UNKNOWN.typeName)
        assertEquals(NetworkStatus.NOT_REACHABLE.value, ConnectionType.NONE.typeName)
    }
}
