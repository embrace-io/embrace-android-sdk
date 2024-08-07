package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.fakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class NetworkSpanForwardingBehaviorImplTest {

    @Test
    fun testDefault() {
        with(fakeNetworkSpanForwardingBehavior()) {
            assertFalse(isNetworkSpanForwardingEnabled())
        }
    }

    @Test
    fun testRemote() {
        with(fakeNetworkSpanForwardingBehavior(remoteConfig = { remoteEnabled })) {
            assertTrue(isNetworkSpanForwardingEnabled())
        }

        with(fakeNetworkSpanForwardingBehavior(remoteConfig = { remoteDisabled })) {
            assertFalse(isNetworkSpanForwardingEnabled())
        }
    }

    companion object {
        private val remoteEnabled = NetworkSpanForwardingRemoteConfig(pctEnabled = 100.0f)
        private val remoteDisabled = NetworkSpanForwardingRemoteConfig(pctEnabled = 0.0f)
    }
}
