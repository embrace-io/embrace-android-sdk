package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityPaused
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityResumed
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityStarted
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.Backgrounded
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.NavControllerAttached
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.NavControllerDestinationChanged
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
    private lateinit var navActivityController: ActivityController<NavControllerActivity>

    @Before
    fun setUp() {
        clock = FakeClock()
        events = mutableListOf()
        activityController = Robolectric.buildActivity(DopeActivity::class.java).create()
        anotherController = Robolectric.buildActivity(CoolActivity::class.java).create()
        navActivityController = Robolectric.buildActivity(NavControllerActivity::class.java).create()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `activity with NavController produces nav events before activity events in P`() {
        createTracker(true).assertNavControllerActivityOpening(true)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `activity with NavController produces nav events before activity events in Q`() {
        createTracker(true).assertNavControllerActivityOpening(true)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `detection disabled produces only activity events even with NavController in P`() {
        createTracker(false).assertNavControllerActivityOpening(false)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `detection disabled produces only activity events even with NavController in Q`() {
        createTracker(false).assertNavControllerActivityOpening(false)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `activity transitions without NavController produce the right events in P`() {
        createTracker(true).assertPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `activity transitions without NavController produce the right events in Q`() {
        createTracker(true).assertPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `concurrent activities without NavController produce the right events in P`() {
        createTracker(true).assertConcurrentPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `concurrent activities without NavController produce the right events in Q`() {
        createTracker(true).assertConcurrentPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `transition from NavController activity to plain activity in P`() {
        createTracker(true).assertNavControllerToPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `transition from NavController activity to plain activity in Q`() {
        createTracker(true).assertNavControllerToPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `transition from plain activity to NavController activity in P`() {
        createTracker(true).assertPlainToNavControllerActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `transition from plain activity to NavController activity in Q`() {
        createTracker(true).assertPlainToNavControllerActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `NavController activity background and foreground produces no duplicate nav events in P`() {
        createTracker(true).assertNavControllerActivityForegrounding()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `NavController activity background and foreground produces no duplicate nav events in Q`() {
        createTracker(true).assertNavControllerActivityForegrounding()
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

    private fun ActivityNavigationTracker.assertNavControllerActivityOpening(trackNav: Boolean) {
        val times = openActivity(navActivityController)
        assertEquals(4, times.size)
        val expectedEvents: List<NavigationEvent> = if (trackNav) {
            mutableListOf(
                NavControllerAttached(navActivityController.get(), times[0]),
                NavControllerDestinationChanged(navActivityController.get(), "home", times[0])
            )
        } else {
            listOf()
        }

        assertEvents(
            expectedEvents + listOf(
                ActivityStarted(navActivityController.get(), times[0]),
                ActivityResumed(navActivityController.get(), times[1]),
                ActivityPaused(navActivityController.get(), times[2]),
                Backgrounded(times[3]),
            )
        )
    }

    private fun ActivityNavigationTracker.assertNavControllerToPlainActivityNavigation() {
        val times = transitionBetweenActivities(navActivityController, activityController)
        assertEquals(7, times.size)
        assertEvents(
            NavControllerAttached(navActivityController.get(), times[0]),
            NavControllerDestinationChanged(navActivityController.get(), "home", times[0]),
            ActivityStarted(navActivityController.get(), times[0]),
            ActivityResumed(navActivityController.get(), times[1]),
            ActivityPaused(navActivityController.get(), times[2]),
            ActivityStarted(activityController.get(), times[3]),
            ActivityResumed(activityController.get(), times[4]),
            ActivityPaused(activityController.get(), times[5]),
            Backgrounded(times[6]),
        )
    }

    private fun ActivityNavigationTracker.assertPlainToNavControllerActivityNavigation() {
        val times = transitionBetweenActivities(activityController, navActivityController)
        assertEquals(7, times.size)
        assertEvents(
            ActivityStarted(activityController.get(), times[0]),
            ActivityResumed(activityController.get(), times[1]),
            ActivityPaused(activityController.get(), times[2]),
            NavControllerAttached(navActivityController.get(), times[3]),
            NavControllerDestinationChanged(navActivityController.get(), "home", times[3]),
            ActivityStarted(navActivityController.get(), times[3]),
            ActivityResumed(navActivityController.get(), times[4]),
            ActivityPaused(navActivityController.get(), times[5]),
            Backgrounded(times[6]),
        )
    }

    private fun ActivityNavigationTracker.assertNavControllerActivityForegrounding() {
        openActivity(navActivityController)
        val navControllerEventCount = events.filter {
            it is NavControllerAttached || it is NavControllerDestinationChanged
        }.size
        openActivity(navActivityController)

        // NavController events should not fire again
        assertEquals(
            navControllerEventCount,
            events.filter {
                it is NavControllerAttached || it is NavControllerDestinationChanged
            }.size
        )
    }

    private fun ActivityNavigationTracker.transitionBetweenActivities(
        first: ActivityController<out Activity>,
        second: ActivityController<out Activity>,
    ): List<Long> {
        return invokeCallbacks(
            listOf(
                first::start,
                first::resume,
                first::pause,
                second::start,
                second::resume,
                second::pause,
                ::onBackground
            )
        )
    }

    private fun ActivityNavigationTracker.openConcurrentActivities(
        first: ActivityController<out Activity>,
        second: ActivityController<out Activity>,
    ): List<Long> {
        return invokeCallbacks(
            listOf(
                first::start,
                first::resume,
                second::start,
                second::resume,
                first::pause,
                second::pause,
                ::onBackground
            )
        )
    }

    private fun ActivityNavigationTracker.openActivity(
        activity: ActivityController<out Activity>,
    ): List<Long> {
        return invokeCallbacks(
            listOf(
                activity::start,
                activity::resume,
                activity::pause,
                ::onBackground
            )
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

    private fun createTracker(trackNav: Boolean): ActivityNavigationTracker {
        return ActivityNavigationTracker(clock, events::add, trackNav, FakeInternalLogger()).apply {
            RuntimeEnvironment.getApplication().registerActivityLifecycleCallbacks(this)
        }
    }

    private fun assertEvents(vararg expected: NavigationEvent) {
        assertEvents(expected.toList())
    }

    private fun assertEvents(expected: List<NavigationEvent>) {
        assertEquals(expected, events)
    }

    private class NavControllerActivity : FragmentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val navHostFragment = NavHostFragment()
            supportFragmentManager.beginTransaction()
                .add(android.R.id.content, navHostFragment)
                .commitNow()
            navHostFragment.navController.graph = navHostFragment.navController.createGraph(startDestination = "home") {
                fragment<androidx.fragment.app.Fragment>("home")
            }
        }
    }

    private class DopeActivity : Activity()
    private class CoolActivity : Activity()
}
