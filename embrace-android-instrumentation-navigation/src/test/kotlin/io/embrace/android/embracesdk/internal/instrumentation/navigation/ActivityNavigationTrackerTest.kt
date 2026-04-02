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
        transitionBetweenActivities(activityController, anotherController)
        assertEvents(
            ActivityStarted(activityController.get(), clock.now()),
            ActivityResumed(activityController.get(), clock.now()),
            ActivityPaused(activityController.get(), clock.now()),
            ActivityStarted(anotherController.get(), clock.now()),
            ActivityResumed(anotherController.get(), clock.now()),
            ActivityPaused(anotherController.get(), clock.now()),
            Backgrounded(clock.now()),
        )
    }

    private fun ActivityNavigationTracker.assertConcurrentPlainActivityNavigation() {
        openConcurrentActivities(activityController, anotherController)
        assertEvents(
            ActivityStarted(activityController.get(), clock.now()),
            ActivityResumed(activityController.get(), clock.now()),
            ActivityStarted(anotherController.get(), clock.now()),
            ActivityResumed(anotherController.get(), clock.now()),
            ActivityPaused(activityController.get(), clock.now()),
            ActivityPaused(anotherController.get(), clock.now()),
            Backgrounded(clock.now()),
        )
    }

    private fun ActivityNavigationTracker.assertNavControllerActivityOpening(trackNav: Boolean) {
        openActivity(navActivityController)
        val expectedEvents: List<NavigationEvent> = if (trackNav) {
            mutableListOf(
                NavControllerAttached(navActivityController.get(), clock.now()),
                NavControllerDestinationChanged(navActivityController.get(), "home", clock.now())
            )
        } else {
            listOf()
        }

        assertEvents(
            expectedEvents + listOf(
                ActivityStarted(navActivityController.get(), clock.now()),
                ActivityResumed(navActivityController.get(), clock.now()),
                ActivityPaused(navActivityController.get(), clock.now()),
                Backgrounded(clock.now()),
            )
        )
    }

    private fun ActivityNavigationTracker.assertNavControllerToPlainActivityNavigation() {
        transitionBetweenActivities(navActivityController, activityController)
        assertEvents(
            NavControllerAttached(navActivityController.get(), clock.now()),
            NavControllerDestinationChanged(navActivityController.get(), "home", clock.now()),
            ActivityStarted(navActivityController.get(), clock.now()),
            ActivityResumed(navActivityController.get(), clock.now()),
            ActivityPaused(navActivityController.get(), clock.now()),
            ActivityStarted(activityController.get(), clock.now()),
            ActivityResumed(activityController.get(), clock.now()),
            ActivityPaused(activityController.get(), clock.now()),
            Backgrounded(clock.now()),
        )
    }

    private fun ActivityNavigationTracker.assertPlainToNavControllerActivityNavigation() {
        transitionBetweenActivities(activityController, navActivityController)
        assertEvents(
            ActivityStarted(activityController.get(), clock.now()),
            ActivityResumed(activityController.get(), clock.now()),
            ActivityPaused(activityController.get(), clock.now()),
            NavControllerAttached(navActivityController.get(), clock.now()),
            NavControllerDestinationChanged(navActivityController.get(), "home", clock.now()),
            ActivityStarted(navActivityController.get(), clock.now()),
            ActivityResumed(navActivityController.get(), clock.now()),
            ActivityPaused(navActivityController.get(), clock.now()),
            Backgrounded(clock.now()),
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
    ) {
        first.start()
        first.resume()
        first.pause()
        second.start()
        second.resume()
        first.stop()
        second.pause()
        second.stop()
        onBackground()
    }

    private fun ActivityNavigationTracker.openConcurrentActivities(
        first: ActivityController<out Activity>,
        second: ActivityController<out Activity>,
    ) {
        first.start()
        first.resume()
        second.start()
        second.resume()
        first.pause()
        first.stop()
        second.pause()
        second.stop()
        onBackground()
    }

    private fun ActivityNavigationTracker.openActivity(
        activity: ActivityController<out Activity>,
    ) {
        activity.start()
        activity.resume()
        activity.pause()
        activity.stop()
        onBackground()
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
