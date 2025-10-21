package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.internal.config.remote.NetworkSpanForwardingRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class NetworkSpanForwardingBehaviorImplTest {

    @Test
    fun testDefault() {
        with(createNetworkSpanForwardingBehavior()) {
            assertFalse(isNetworkSpanForwardingEnabled())
        }
    }

    @Test
    fun testRemote() {
        with(createNetworkSpanForwardingBehavior(remoteConfig = remoteEnabled)) {
            assertTrue(isNetworkSpanForwardingEnabled())
        }

        with(createNetworkSpanForwardingBehavior(remoteConfig = remoteDisabled)) {
            assertFalse(isNetworkSpanForwardingEnabled())
        }
    }

    companion object {
        private val remoteEnabled =
            RemoteConfig(networkSpanForwardingRemoteConfig = NetworkSpanForwardingRemoteConfig(pctEnabled = 100.0f))
        private val remoteDisabled =
            RemoteConfig(networkSpanForwardingRemoteConfig = NetworkSpanForwardingRemoteConfig(pctEnabled = 0.0f))
    }
}
