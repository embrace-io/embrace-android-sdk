package io.embrace.android.embracesdk.internal.config.behavior

import io.embrace.android.embracesdk.fakes.createBackgroundActivityBehavior
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

internal class BackgroundActivityBehaviorImplTest {

    private val remote = BackgroundActivityRemoteConfig(
        0f
    )

    @Test
    fun testDefaults() {
        with(createBackgroundActivityBehavior()) {
            assertFalse(isBackgroundActivityCaptureEnabled())
            assertEquals(100, getManualBackgroundActivityLimit())
            assertEquals(5000L, getMinBackgroundActivityDuration())
            assertEquals(30, getMaxCachedActivities())
        }
    }

    @Test
    fun testRemoteAndLocal() {
        with(createBackgroundActivityBehavior(remoteCfg = { remote })) {
            assertFalse(isBackgroundActivityCaptureEnabled())
            assertEquals(100, getManualBackgroundActivityLimit())
            assertEquals(5000L, getMinBackgroundActivityDuration())
            assertEquals(30, getMaxCachedActivities())
        }
    }
}
