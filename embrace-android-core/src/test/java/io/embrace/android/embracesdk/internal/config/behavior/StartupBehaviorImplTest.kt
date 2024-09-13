package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createStartupBehavior
import io.embrace.android.embracesdk.internal.config.local.StartupMomentLocalConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StartupBehaviorImplTest {

    private val local = StartupMomentLocalConfig(
        automaticallyEnd = false
    )

    @Test
    fun testDefaults() {
        with(createStartupBehavior()) {
            assertTrue(isStartupMomentAutoEndEnabled())
        }
    }

    @Test
    fun testLocalOnly() {
        with(createStartupBehavior(localCfg = { local })) {
            assertFalse(isStartupMomentAutoEndEnabled())
        }
    }
}
