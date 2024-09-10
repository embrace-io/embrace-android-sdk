package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createAppExitInfoBehavior
import io.embrace.android.embracesdk.internal.config.local.AppExitInfoLocalConfig
import io.embrace.android.embracesdk.internal.config.remote.AppExitInfoConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class AppExitInfoBehaviorImplTest {

    private val local = AppExitInfoLocalConfig(33792, false)

    private val remote = RemoteConfig(
        appExitInfoConfig = AppExitInfoConfig(55209, 100f)
    )

    @Test
    fun testDefaults() {
        with(createAppExitInfoBehavior()) {
            assertEquals(2097152, getTraceMaxLimit())
            assertTrue(isEnabled())
        }
    }

    @Test
    fun testLocalOnly() {
        with(createAppExitInfoBehavior(localCfg = { local })) {
            assertEquals(33792, getTraceMaxLimit())
            assertFalse(isEnabled())
        }
    }

    @Test
    fun testLocalAndRemote() {
        with(createAppExitInfoBehavior(localCfg = { local }, remoteCfg = { remote })) {
            assertEquals(55209, getTraceMaxLimit())
            assertTrue(isEnabled())
        }
    }
}
