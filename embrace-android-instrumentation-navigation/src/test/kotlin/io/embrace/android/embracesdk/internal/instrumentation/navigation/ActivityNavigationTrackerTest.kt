package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
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
    private lateinit var tracker: ActivityNavigationTracker
    private lateinit var activityController: ActivityController<DopeActivity>
    private lateinit var anotherController: ActivityController<CoolActivity>

    @Before
    fun setUp() {
        clock = FakeClock()
        events = mutableListOf()
        tracker = ActivityNavigationTracker(clock, events::add)
        RuntimeEnvironment.getApplication().registerActivityLifecycleCallbacks(tracker)
        activityController = Robolectric.buildActivity(DopeActivity::class.java).create()
        anotherController = Robolectric.buildActivity(CoolActivity::class.java).create()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `transition from start to background to foreground produce the right events in P`() {
        simulateActivityTransition()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `transition from start to background to foreground produce the right events in Q`() {
        simulateActivityTransition()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `opening concurrent visible activities produces the right events in P`() {
        simulateConcurrentActivities()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `opening concurrent visible activities produces the right events in Q`() {
        simulateConcurrentActivities()
    }

    private fun simulateActivityTransition() {
        var totalEvents = 0
        activityController.start()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityStarted(activityController.get(), clock.now()))
        activityController.resume()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityResumed(activityController.get(), clock.now()))
        activityController.pause()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityPaused(activityController.get(), clock.now()))
        anotherController.start()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityStarted(anotherController.get(), clock.now()))
        anotherController.resume()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityResumed(anotherController.get(), clock.now()))
        activityController.stop()
        assertEquals(totalEvents, events.size)
        anotherController.pause()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityPaused(anotherController.get(), clock.now()))
        anotherController.stop()
        assertEquals(totalEvents, events.size)
        tracker.onBackground()
        assertNewEvent(++totalEvents, NavigationEvent.Backgrounded(clock.now()))
    }

    private fun simulateConcurrentActivities() {
        var totalEvents = 0
        activityController.start()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityStarted(activityController.get(), clock.now()))
        activityController.resume()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityResumed(activityController.get(), clock.now()))
        anotherController.start()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityStarted(anotherController.get(), clock.now()))
        anotherController.resume()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityResumed(anotherController.get(), clock.now()))
        activityController.pause()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityPaused(activityController.get(), clock.now()))
        activityController.stop()
        assertEquals(totalEvents, events.size)
        anotherController.pause()
        assertNewEvent(++totalEvents, NavigationEvent.ActivityPaused(anotherController.get(), clock.now()))
        anotherController.stop()
        assertEquals(totalEvents, events.size)
        tracker.onBackground()
        assertNewEvent(++totalEvents, NavigationEvent.Backgrounded(clock.now()))
    }

    private fun assertNewEvent(expectedTotalCount: Int, event: NavigationEvent) {
        assertEquals(expectedTotalCount, events.size)
        assertEquals(event, events.last())
    }

    private class DopeActivity : Activity()
    private class CoolActivity : Activity()
}
