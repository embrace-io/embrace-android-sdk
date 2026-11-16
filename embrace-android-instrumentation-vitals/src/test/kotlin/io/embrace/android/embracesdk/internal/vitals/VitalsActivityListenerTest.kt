package io.embrace.android.embracesdk.internal.vitals

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeInstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityCreated
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity
import org.robolectric.Shadows.shadowOf
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
internal class VitalsActivityListenerTest {

    private val fakeStrategy = object : FrameMetricsStrategy {
        override fun vsyncNanos(frameMetrics: FrameMetrics): Long = 0L
        override fun jankNanos(frameMetrics: FrameMetrics): Long = 0L
        override fun frameIntervalNanos(frameMetrics: FrameMetrics): Long = 0L
    }

    private lateinit var args: FakeInstrumentationArgs
    private val focalCallbacks = FakeFocalInteractionCallbacks()
    private lateinit var listener: VitalsActivityListener

    @Before
    fun setUp() {
        val application: Application = ApplicationProvider.getApplicationContext()
        args = FakeInstrumentationArgs(application)
        listener = VitalsActivityListener(
            focalCallbacks = focalCallbacks,
            navSource = ActivityNavigationSource(callbacks = focalCallbacks),
            frameMetricsHandler = Handler(Looper.getMainLooper()),
            frameMetricsStrategy = fakeStrategy,
            eventBus = args.eventBus,
            errorHandler = args.errorHandler,
        )
        listener.subscribe()
    }

    @Test
    fun `onActivityResumed wraps the window callback and fires onScreenStart`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        resume(activity)

        assertTrue(activity.window.callback is VitalsWindowCallback)
        assertEquals(1, focalCallbacks.screenStartCount)
        assertTrue(args.errorHandler.errorMessages.isEmpty())
    }

    @Test
    fun `onActivityResumed does not double-wrap the window callback`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        resume(activity)
        val firstCallback = activity.window.callback
        resume(activity)

        assertSame(firstCallback, activity.window.callback)
    }

    @Test
    fun `onActivityResumed does not re-wrap when a foreign callback sits on top`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        // First resume installs our wrapper.
        resume(activity)
        // Another SDK (or compose-tap) then wraps our callback, so ours is no longer outermost.
        val foreign: Window.Callback = object : Window.Callback by activity.window.callback {}
        activity.window.callback = foreign

        // A later resume must not add a second VitalsWindowCallback layer.
        resume(activity)

        assertSame(foreign, activity.window.callback)
    }

    @Test
    fun `close makes installed window callbacks inert`() {
        val activity = buildActivity(Activity::class.java).setup().get()
        resume(activity)

        listener.close()
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
        try {
            activity.window.callback.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }

        assertEquals(0, focalCallbacks.interactionStartCount)
    }

    @Test
    fun `onActivityResumed does not wrap the window callback after close`() {
        val activity = buildActivity(Activity::class.java).setup().get()
        val original = activity.window.callback
        listener.close()
        resume(activity)
        assertSame(original, activity.window.callback)
    }

    @Test
    fun `close off the main thread removes frame-metrics listeners on the main thread`() {
        val window = mockk<Window>(relaxed = true)
        val activity = mockk<Activity>(relaxed = true) { every { this@mockk.window } returns window }
        resume(activity)

        thread { listener.close() }.join()
        verify(exactly = 0) { window.removeOnFrameMetricsAvailableListener(any()) }

        shadowOf(Looper.getMainLooper()).idle()
        verify(exactly = 1) { window.removeOnFrameMetricsAvailableListener(any()) }
    }

    @Test
    fun `onActivityPaused fires onScreenStop`() {
        val activity = buildActivity(Activity::class.java).setup().get()
        resume(activity)

        pause(activity)

        assertEquals(1, focalCallbacks.screenStopCount)
    }

    @Test
    fun `onActivityResumed reports a navigation end for the destination`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        val eventTime = SystemClock.uptimeMillis()
        resume(activity)

        assertEquals(listOf(activity.localClassName to eventTime), focalCallbacks.navigationEnds)
    }

    @Test
    fun `onActivityCreated reports a navigation start once past the cold-start activity`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        create(activity, null) // cold start: skipped
        assertTrue(focalCallbacks.navigationStarts.isEmpty())

        val eventTime = SystemClock.uptimeMillis()
        create(activity, null) // a subsequent (forward) navigation
        assertEquals(listOf(activity.localClassName to eventTime), focalCallbacks.navigationStarts)
    }

    @Test
    fun `a recreated activity reports no navigation start`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        create(activity, null) // cold start: skipped
        create(activity, Bundle()) // recreated from saved state: skipped
        assertTrue(focalCallbacks.navigationStarts.isEmpty())

        val eventTime = SystemClock.uptimeMillis()
        create(activity, null) // a forward navigation
        assertEquals(listOf(activity.localClassName to eventTime), focalCallbacks.navigationStarts)
    }

    @Test
    fun `resume adds and pause removes the frame-metrics listener`() {
        val window = mockk<Window>(relaxed = true)
        val activity = mockk<Activity>(relaxed = true) { every { this@mockk.window } returns window }

        resume(activity)
        verify(exactly = 1) { window.addOnFrameMetricsAvailableListener(any(), any()) }

        pause(activity)
        verify(exactly = 1) { window.removeOnFrameMetricsAvailableListener(any()) }
    }

    @Test
    fun `activity events are not handled after close`() {
        val activity = buildActivity(Activity::class.java).setup().get()
        listener.close()

        create(activity, null)
        create(activity, null)
        resume(activity)
        pause(activity)

        assertTrue(focalCallbacks.navigationStarts.isEmpty())
        assertEquals(0, focalCallbacks.screenStartCount)
        assertEquals(0, focalCallbacks.screenStopCount)
    }

    private fun create(activity: Activity, savedInstanceState: Bundle?) =
        args.eventBus.emit(ActivityLifecycle.CREATED, ActivityCreated(activity, savedInstanceState))

    private fun resume(activity: Activity) = args.eventBus.emit(ActivityLifecycle.RESUMED, activity)

    private fun pause(activity: Activity) = args.eventBus.emit(ActivityLifecycle.PAUSED, activity)
}
