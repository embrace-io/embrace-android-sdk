package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createStartupBehavior
import org.junit.Assert.assertTrue
import org.junit.Test

internal class StartupBehaviorImplTest {

    @Test
    fun testDefaults() {
        with(createStartupBehavior()) {
            assertTrue(isStartupMomentAutoEndEnabled())
        }
    }
}
