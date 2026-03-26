package io.embrace.android.embracesdk.internal.capture.connectivity

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
    fun `verify conversion to optimistic connectivity status`() {
        assertEquals(OptimisticWifi, ConnectionType.WIFI.toOptimisticStatus())
        assertEquals(OptimisticWan, ConnectionType.WAN.toOptimisticStatus())
        assertEquals(OptimisticUnknown, ConnectionType.UNKNOWN.toOptimisticStatus())
        assertEquals(ConnectivityStatus.None, ConnectionType.NONE.toOptimisticStatus())
    }
}
