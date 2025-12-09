package io.embrace.android.embracesdk.internal.api

import io.embrace.android.embracesdk.internal.api.delegate.NoopEmbraceInternalInterface
import io.embrace.android.embracesdk.internal.api.delegate.NoopInternalTracingApi
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

internal class NoopEmbraceInternalInterfaceTest {

    private lateinit var impl: NoopEmbraceInternalInterface

    @Before
    fun setUp() {
        impl = NoopEmbraceInternalInterface(
            NoopInternalTracingApi()
        )
    }

    @Test
    fun `check isNetworkSpanForwardingEnabled before SDK starts`() {
        assertFalse(impl.isNetworkSpanForwardingEnabled())
    }
}
