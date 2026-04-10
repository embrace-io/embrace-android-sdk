package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeNavigationTrackingService
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityPaused
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityResumed
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityStarted
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.Backgrounded
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
internal class ActivityNavigationTrackerTest {

    private lateinit var clock: FakeClock
    private lateinit var events: MutableList<NavigationEvent>
    private lateinit var activityController: ActivityController<DopeActivity>
    private lateinit var anotherController: ActivityController<CoolActivity>
    private lateinit var trackedActivities: MutableList<Activity>

    @Before
    fun setUp() {
        clock = FakeClock()
        events = mutableListOf()
        trackedActivities = mutableListOf()
        activityController = Robolectric.buildActivity(DopeActivity::class.java).create()
        anotherController = Robolectric.buildActivity(CoolActivity::class.java).create()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `activity transitions produce the right events in P`() {
        createTracker().assertPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `activity transitions produce the right events in Q`() {
        createTracker().assertPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `concurrent activities produce the right events in P`() {
        createTracker().assertConcurrentPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `concurrent activities produce the right events in Q`() {
        createTracker().assertConcurrentPlainActivityNavigation()
    }

    private fun ActivityNavigationTracker.assertPlainActivityNavigation() {
        val times = transitionBetweenActivities(activityController, anotherController)
        assertEquals(7, times.size)
        assertEvents(
            ActivityStarted(activityController.get(), times[0]),
            ActivityResumed(activityController.get(), times[1]),
            ActivityPaused(activityController.get(), times[2]),
            ActivityStarted(anotherController.get(), times[3]),
            ActivityResumed(anotherController.get(), times[4]),
            ActivityPaused(anotherController.get(), times[5]),
            Backgrounded(times[6]),
        )
    }

    private fun ActivityNavigationTracker.assertConcurrentPlainActivityNavigation() {
        val times = openConcurrentActivities(activityController, anotherController)
        assertEquals(7, times.size)
        assertEvents(
            ActivityStarted(activityController.get(), times[0]),
            ActivityResumed(activityController.get(), times[1]),
            ActivityStarted(anotherController.get(), times[2]),
            ActivityResumed(anotherController.get(), times[3]),
            ActivityPaused(activityController.get(), times[4]),
            ActivityPaused(anotherController.get(), times[5]),
            Backgrounded(times[6]),
        )
    }

    private fun ActivityNavigationTracker.transitionBetweenActivities(
        first: ActivityController<out Activity>,
        second: ActivityController<out Activity>,
    ): List<Long> {
        return invokeCallbacks(
            listOf(first::start, first::resume, first::pause, second::start, second::resume, second::pause, ::onBackground)
        )
    }

    private fun ActivityNavigationTracker.openConcurrentActivities(
        first: ActivityController<out Activity>,
        second: ActivityController<out Activity>,
    ): List<Long> {
        return invokeCallbacks(
            listOf(first::start, first::resume, second::start, second::resume, first::pause, second::pause, ::onBackground)
        )
    }

    private fun invokeCallbacks(callbacks: List<Function0<Any>>): List<Long> {
        val times = mutableListOf<Long>()
        callbacks.forEach {
            times.add(clock.tick())
            it()
        }
        return times
    }

    private fun createTracker(): ActivityNavigationTracker {
        return ActivityNavigationTracker(clock, events::add, FakeNavigationTrackingService()).apply {
            RuntimeEnvironment.getApplication().registerActivityLifecycleCallbacks(this)
        }
    }

    private fun assertEvents(vararg expected: NavigationEvent) {
        assertEquals(expected.toList(), events)
    }

    private class DopeActivity : Activity()
    private class CoolActivity : Activity()
}
