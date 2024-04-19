package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.remote.OTelRemoteConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.fakeOTelBehavior
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OTelBehaviorTest {

    private val remoteAllOn = RemoteConfig(
        oTelConfig = OTelRemoteConfig(
            isStableEnabled = true,
            isBetaEnabled = true,
            isDevEnabled = true
        ),
    )

    private val remoteAllOff = RemoteConfig(
        oTelConfig = OTelRemoteConfig(
            isStableEnabled = false,
            isBetaEnabled = false,
            isDevEnabled = false
        ),
    )

    @Test
    fun testDefaults() {
        with(fakeOTelBehavior()) {
            assertTrue(isStableEnabled())
            assertTrue(isBetaEnabled())
            assertFalse(isDevEnabled())
        }
    }

    @Test
    fun `flags can be turned on remotely`() {
        with(fakeOTelBehavior(remoteCfg = { remoteAllOn })) {
            assertTrue(isStableEnabled())
            assertTrue(isBetaEnabled())
            assertTrue(isDevEnabled())
        }
    }

    @Test
    fun `flags can be turned off remotely`() {
        with(fakeOTelBehavior(remoteCfg = { remoteAllOff })) {
            assertFalse(isStableEnabled())
            assertFalse(isBetaEnabled())
            assertFalse(isDevEnabled())
        }
    }
}
