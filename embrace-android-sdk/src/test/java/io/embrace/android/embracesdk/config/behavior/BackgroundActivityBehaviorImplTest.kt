package io.embrace.android.embracesdk.config.behavior

import io.embrace.android.embracesdk.config.local.BackgroundActivityLocalConfig
import io.embrace.android.embracesdk.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.fakes.fakeBackgroundActivityBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BackgroundActivityBehaviorImplTest {

    private val local = BackgroundActivityLocalConfig(
        true,
        50,
        3000L,
        50
    )

    private val remote = BackgroundActivityRemoteConfig(
        0f
    )

    @Test
    fun testDefaults() {
        with(fakeBackgroundActivityBehavior()) {
            assertFalse(isEnabled())
            assertEquals(100, getManualBackgroundActivityLimit())
            assertEquals(5000L, getMinBackgroundActivityDuration())
            assertEquals(30, getMaxCachedActivities())
        }
    }

    @Test
    fun testLocalOnly() {
        with(fakeBackgroundActivityBehavior(localCfg = { local })) {
            assertTrue(isEnabled())
            assertEquals(50, getManualBackgroundActivityLimit())
            assertEquals(3000L, getMinBackgroundActivityDuration())
            assertEquals(50, getMaxCachedActivities())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(fakeBackgroundActivityBehavior(localCfg = { local }, remoteCfg = { remote })) {
            assertFalse(isEnabled())
            assertEquals(50, getManualBackgroundActivityLimit())
            assertEquals(3000L, getMinBackgroundActivityDuration())
            assertEquals(50, getMaxCachedActivities())
        }
    }
}
