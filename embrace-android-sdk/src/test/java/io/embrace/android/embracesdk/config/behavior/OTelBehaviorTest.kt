package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.remote.OTelConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.fakeOTelBehavior
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class OTelBehaviorTest {

    private val remote = RemoteConfig(
        oTelConfig = OTelConfig(
            useV2SessionPayload = true,
            useV2LogPayload = true,
            useV2CrashPayload = true
        ),
    )

    @Test
    fun testDefaults() {
        with(fakeOTelBehavior()) {
            assertFalse(useV2SessionPayload())
            assertFalse(useV2LogPayload())
            assertFalse(useV2CrashPayload())
        }
    }

    @Test
    fun testRemote() {
        with(fakeOTelBehavior(remoteCfg = { remote })) {
            assertTrue(useV2SessionPayload())
            assertTrue(useV2LogPayload())
            assertTrue(useV2CrashPayload())
        }
    }
}
