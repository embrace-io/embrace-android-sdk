package io.embrace.android.embracesdk.internal.capture.connectivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class ConnectivityStatusTest {

    @Test
    fun `optimistic constants map properly`() {
        assertEquals(OptimisticWifi, ConnectivityStatus.Wifi(true))
        assertEquals(OptimisticWan, ConnectivityStatus.Wan(true))
        assertEquals(OptimisticUnknown, ConnectivityStatus.Unknown(true))
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
}
