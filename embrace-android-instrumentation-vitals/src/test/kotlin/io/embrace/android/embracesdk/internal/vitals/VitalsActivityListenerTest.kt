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
import io.embrace.android.embracesdk.internal.arch.vitals.FocalMomentType
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
        override fun frameStartNanos(frameMetrics: FrameMetrics): Long = 0L
        override fun jankBudgetNanos(frameMetrics: FrameMetrics): Long = Long.MAX_VALUE
        override fun frameIntervalNanos(frameMetrics: FrameMetrics): Long = 0L
    }

    private lateinit var args: FakeInstrumentationArgs
    private val focalCallbacks = FakeFocalInteractionCallbacks()
    private lateinit var listener: VitalsActivityListener

    // every focal moment trigger emitted on the bus
    private val triggers = mutableListOf<FocalMomentType>()

    @Before
    fun setUp() {
        val application: Application = ApplicationProvider.getApplicationContext()
        args = FakeInstrumentationArgs(application)
        args.eventBus.addHandler(FocalMomentType.KEY) { triggers += it }
        listener = VitalsActivityListener(
            focalCallbacks = focalCallbacks,
            navSource = ActivityNavigationSource(callbacks = focalCallbacks, eventBus = args.eventBus),
            frameMetricsHandler = Handler(Looper.getMainLooper()),
            frameMetricsStrategy = fakeStrategy,
            eventBus = args.eventBus,
            errorHandler = args.errorHandler,
            versionChecker = args.versionChecker,
        )
        listener.subscribe()
    }

    @Test
    fun `onActivityResumed wraps the window callback`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        resume(activity)

        assertTrue(activity.window.callback is VitalsWindowCallback)
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
    fun `onActivityResumed reports a navigation end for the destination`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        val eventTime = SystemClock.uptimeMillis()
        resume(activity)

        assertEquals(listOf(activity.localClassName to eventTime), focalCallbacks.navigationEnds)
    }

    @Test
    fun `onActivityCreated reports a navigation start and triggers a screen load once past the cold-start activity`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        create(activity, null) // cold start: skipped
        assertTrue(focalCallbacks.navigationStarts.isEmpty())
        assertTrue("cold start triggers no focal moment", triggers.isEmpty())

        val eventTime = SystemClock.uptimeMillis()
        create(activity, null) // a subsequent (forward) navigation
        assertEquals(listOf(activity.localClassName to eventTime), focalCallbacks.navigationStarts)
        assertEquals(listOf(FocalMomentType.SCREEN_LOAD), triggers)
    }

    @Test
    fun `a recreated activity reports no navigation start and triggers no focal moment`() {
        val activity = buildActivity(Activity::class.java).setup().get()

        create(activity, null) // cold start: skipped
        create(activity, Bundle()) // recreated from saved state: skipped
        assertTrue(focalCallbacks.navigationStarts.isEmpty())
        assertTrue(triggers.isEmpty())

        val eventTime = SystemClock.uptimeMillis()
        create(activity, null) // a forward navigation
        assertEquals(listOf(activity.localClassName to eventTime), focalCallbacks.navigationStarts)
        assertEquals(listOf(FocalMomentType.SCREEN_LOAD), triggers)
    }

    @Test
    fun `resuming an existing activity triggers no focal moment, after a pause or a back navigation`() {
        val home = buildActivity(Activity::class.java).setup().get()
        val detail = buildActivity(Activity::class.java).setup().get()
        create(home, null) // cold start
        resume(home)

        // a dialog over it, or the app backgrounded and brought back
        pause(home)
        resume(home)
        assertTrue(triggers.isEmpty())

        pause(home)
        create(detail, null) // forward navigation
        resume(detail)

        // back navigation resumes the existing activity rather than creating one, and the system back gesture doesn't reach the
        // window as a touch either, so returning to a screen opens no focal moment at all
        pause(detail)
        resume(home)
        assertEquals("only the forward navigation", listOf(FocalMomentType.SCREEN_LOAD), triggers)
    }

    @Test
    fun `the activity a trampoline launches and a warm start each trigger a screen load`() {
        val splash = buildActivity(Activity::class.java).setup().get()
        val main = buildActivity(Activity::class.java).setup().get()
        val relaunched = buildActivity(Activity::class.java).setup().get()
        create(splash, null) // cold start
        resume(splash)

        // the splash immediately launches the real first screen: the second creation, so not skipped as the cold start
        pause(splash)
        create(main, null)
        resume(main)
        assertEquals(listOf(FocalMomentType.SCREEN_LOAD), triggers)

        // backgrounded and its activities destroyed while the process lives on, then relaunched from the launcher: a fresh
        // activity with no saved state is indistinguishable from a forward navigation
        pause(main)
        create(relaunched, null)
        resume(relaunched)
        assertEquals(listOf(FocalMomentType.SCREEN_LOAD, FocalMomentType.SCREEN_LOAD), triggers)
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
        assertTrue(focalCallbacks.navigationEnds.isEmpty())
        assertTrue(triggers.isEmpty())
    }

    private fun create(activity: Activity, savedInstanceState: Bundle?) =
        args.eventBus.emit(ActivityLifecycle.CREATED, ActivityCreated(activity, savedInstanceState))

    private fun resume(activity: Activity) = args.eventBus.emit(ActivityLifecycle.RESUMED, activity)

    private fun pause(activity: Activity) = args.eventBus.emit(ActivityLifecycle.PAUSED, activity)
}
