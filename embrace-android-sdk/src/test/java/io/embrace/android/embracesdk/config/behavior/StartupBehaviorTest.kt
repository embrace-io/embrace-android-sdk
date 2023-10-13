package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.StartupMomentLocalConfig
import io.embrace.android.embracesdk.fakes.fakeStartupBehavior
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StartupBehaviorTest {

    private val local = StartupMomentLocalConfig(
        automaticallyEnd = false
    )

    @Test
    fun testDefaults() {
        with(fakeStartupBehavior()) {
            assertTrue(isAutomaticEndEnabled())
        }
    }

    @Test
    fun testLocalOnly() {
        with(fakeStartupBehavior(localCfg = { local })) {
            assertFalse(isAutomaticEndEnabled())
        }
    }
}
