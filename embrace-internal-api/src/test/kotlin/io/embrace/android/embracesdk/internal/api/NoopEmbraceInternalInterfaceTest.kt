package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import org.junit.Assert.assertFalse
import org.junit.Test

internal class NoopEmbraceInternalInterfaceTest {

    @Test
    fun `check isNetworkSpanForwardingEnabled before SDK starts`() {
        assertFalse(NoopEmbraceInternalInterface.isNetworkSpanForwardingEnabled())
    }
}
