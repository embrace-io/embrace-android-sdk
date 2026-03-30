package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private lateinit var events: MutableList<NavigationEvent>
    private lateinit var tracker: ActivityNavigationTracker
    private lateinit var activityController: ActivityController<DopeActivity>
    private lateinit var anotherController: ActivityController<CoolActivity>

    @Before
    fun setUp() {
        events = mutableListOf()
        tracker = ActivityNavigationTracker(events::add)
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

    private fun simulateActivityTransition() {
        activityController.start()
        assertEquals(1, events.size)
        assertEquals(NavigationEvent.ActivityStarted(activityController.get().localClassName), events.last())
        activityController.resume()
        assertEquals(1, events.size)
        activityController.pause()
        assertEquals(1, events.size)
        anotherController.start()
        assertEquals(2, events.size)
        assertEquals(NavigationEvent.ActivityStarted(anotherController.get().localClassName), events.last())
        anotherController.resume()
        activityController.stop()
        anotherController.pause()
        assertEquals(2, events.size)
        anotherController.stop()
        assertEquals(3, events.size)
        assertEquals(NavigationEvent.Backgrounded, events.last())
    }

    private class DopeActivity : Activity()
    private class CoolActivity : Activity()
}
