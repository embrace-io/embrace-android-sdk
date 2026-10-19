package io.embrace.android.embracesdk.internal.session

import android.app.Application
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.internal.config.resolved.AutoDataCaptureConfig
import io.embrace.android.embracesdk.internal.config.resolved.EmbraceConfig
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityProcessLifecycleTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.AndroidxProcessLifecycleTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.createLifecycleTracker
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the config gate that decides which [io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker]
 * implementation is used.
 */
internal class LifecycleTrackerFactoryTest {

    private val application = Application()

    @Test
    fun `androidx implementation used by default`() {
        val tracker = createTracker(activityTrackerEnabled = false)
        assertTrue(tracker is AndroidxProcessLifecycleTracker)
    }

    @Test
    fun `activity implementation used when enabled by config`() {
        val tracker = createTracker(activityTrackerEnabled = true)
        assertTrue(tracker is ActivityProcessLifecycleTracker)
    }

    private fun createTracker(activityTrackerEnabled: Boolean) = createLifecycleTracker(
        configService = FakeConfigService(
            config = EmbraceConfig(
                autoDataCapture = {
                    AutoDataCaptureConfig(activityProcessLifecycleTrackerEnabled = { activityTrackerEnabled })
                },
            ),
        ),
        application = application,
        startupContext = application,
    )
}
