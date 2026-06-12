package io.embrace.android.embracesdk.internal.vitals

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity

@RunWith(AndroidJUnit4::class)
internal class VitalsActivityListenerTest {

    private val fakeStrategy = object : FrameMetricsStrategy {
        override fun vsyncNanos(frameMetrics: FrameMetrics): Long = 0L
        override fun jankNanos(frameMetrics: FrameMetrics): Long = 0L
    }

    private lateinit var args: FakeInstrumentationArgs
    private val focalCallbacks = RecordingFocalInteractionCallbacks()
    private lateinit var listener: VitalsActivityListener

    @Before
    fun setUp() {
        val application: Application = ApplicationProvider.getApplicationContext()
        args = FakeInstrumentationArgs(application)
        listener = VitalsActivityListener(
            logger = args.logger,
            focalCallbacks = focalCallbacks,
            frameMetricsHandler = Handler(Looper.getMainLooper()),
            frameMetricsStrategy = fakeStrategy,
        )
    }

    @Test
    fun `onActivityResumed wraps the window callback and fires onScreenStart`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        listener.onActivityResumed(activity)

        assertTrue(activity.window.callback is VitalsWindowCallback)
        assertEquals(1, focalCallbacks.screenStartCount)
        assertTrue(args.logger.errorMessages.isEmpty())
    }

    @Test
    fun `onActivityResumed does not double-wrap the window callback`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        listener.onActivityResumed(activity)
        val firstCallback = activity.window.callback
        listener.onActivityResumed(activity)

        assertSame(firstCallback, activity.window.callback)
    }

    @Test
    fun `onActivityPaused fires onScreenStop`() {
        val activity = buildActivity(Activity::class.java).setup().get()
        listener.onActivityResumed(activity)

        listener.onActivityPaused(activity)

        assertEquals(1, focalCallbacks.screenStopCount)
    }
}
