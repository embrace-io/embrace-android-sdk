package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.remote.OTelRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.fakeOTelBehavior
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OTelBehaviorTest {

    private val remote = RemoteConfig(
        oTelConfig = OTelRemoteConfig(
            isStableEnabled = true,
            isBetaEnabled = true,
            isDevEnabled = true
        ),
    )

    @Test
    fun testDefaults() {
        with(fakeOTelBehavior()) {
            assertTrue(isStableEnabled())
            assertFalse(isBetaEnabled())
            assertFalse(isDevEnabled())
        }
    }

    @Test
    fun testRemote() {
        with(fakeOTelBehavior(remoteCfg = { remote })) {
            assertTrue(isStableEnabled())
            assertTrue(isBetaEnabled())
            assertTrue(isDevEnabled())
        }
    }
}
