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
}
