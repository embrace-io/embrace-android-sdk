package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.R.id.content
import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.createGraph
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.NavControllerAttached
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.NavControllerDestinationChanged
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric.buildActivity

@RunWith(AndroidJUnit4::class)
internal class NavControllerTrackerTest {

    private lateinit var clock: FakeClock
    private lateinit var events: MutableList<NavigationEvent>
    private lateinit var tracker: NavControllerTracker
    private lateinit var logger: FakeInternalLogger
    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        clock = FakeClock()
        events = mutableListOf()
        logger = FakeInternalLogger()
        tracker = NavControllerTracker(events::add, clock, logger)
        activity = createActivity()
    }

    @Test
    fun `tracking activity with nav controller produces attached and destination events`() {
        tracker.track(activity)
        assertEquals(2, events.size)
        assertEquals(NavControllerAttached(activity, clock.now()), events[0])
        assertEquals(NavControllerDestinationChanged(activity, "home", clock.now()), events[1])
    }

    @Test
    fun `track on activity with no nav controller produces no events`() {
        tracker.track(buildActivity(Activity::class.java).setup().get())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `track on FragmentActivity without NavHostFragment produces no events`() {
        tracker.track(buildActivity(FragmentActivity::class.java).setup().get())
        assertTrue(events.isEmpty())
    }

    @Test
    fun `calling track twice on same activity does not produce duplicate events`() {
        tracker.track(activity)
        tracker.track(activity)
        assertEquals(2, events.size)
    }

    @Test
    fun `tracking nav controller to different activity instances produces events for each`() {
        val anotherActivity = createActivity()
        tracker.track(activity)
        tracker.track(anotherActivity)
        assertEquals(4, events.size)
        assertEquals(NavControllerAttached(activity, clock.now()), events[0])
        assertEquals(NavControllerDestinationChanged(activity, "home", clock.now()), events[1])
        assertEquals(NavControllerAttached(anotherActivity, clock.now()), events[2])
        assertEquals(NavControllerDestinationChanged(anotherActivity, "home", clock.now()), events[3])
    }

    @Test
    fun `destination change event screen name falls back to label when route is null`() {
        val labelActivity = createActivity(::graphWithRoutelessDestinationWithLabel)
        tracker.track(labelActivity)
        assertEquals(2, events.size)
        assertEquals("My Home", (events[1] as NavControllerDestinationChanged).name)
    }

    @Test
    fun `destination change event screen name falls back to navigatorName when route and label are null`() {
        val idActivity = createActivity(::graphWithDestinationWithoutRouteAndLabel)
        tracker.track(idActivity)
        assertEquals(2, events.size)
        assertEquals("fragment", (events[1] as NavControllerDestinationChanged).name)
    }

    @Test
    fun `error during tracking is logged and does not crash`() {
        val errorLogger = FakeInternalLogger(throwOnInternalError = false)
        tracker = NavControllerTracker(events::add, clock, errorLogger)
        tracker.track(buildActivity(BrokenNavActivity::class.java).setup().get())
        assertTrue(events.isEmpty())
        assertTrue(
            errorLogger.internalErrorMessages.any {
                it.msg == InternalErrorType.NAV_CONTROLLER_TRACKING_FAIL.toString()
            }
        )
    }

    private fun createActivity(
        navGraphProvider: (navController: NavController) -> NavGraph = { navController: NavController ->
            navController.createGraph(startDestination = "home") {
                fragment<Fragment>("home")
            }
        },
    ): FragmentActivity {
        val activity = buildActivity(FragmentActivity::class.java).setup().get()
        val navHostFragment = NavHostFragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(content, navHostFragment)
            .commitNow()
        val navController = navHostFragment.navController
        navController.graph = navGraphProvider(navController)
        return activity
    }

    private fun graphWithRoutelessDestinationWithLabel(navController: NavController): NavGraph =
        graphWithRoutelessDestination(navController, "My Home")

    private fun graphWithDestinationWithoutRouteAndLabel(navController: NavController): NavGraph =
        graphWithRoutelessDestination(navController, null)

    private fun graphWithRoutelessDestination(
        navController: NavController,
        destinationLabel: CharSequence?,
    ): NavGraph {
        val fragmentNavigator = navController.navigatorProvider.getNavigator(FragmentNavigator::class.java)
        val routelessDestinationId = 1234
        val routelessDestination = fragmentNavigator.createDestination().apply {
            id = routelessDestinationId
            label = destinationLabel
            setClassName(Fragment::class.java.name)
        }
        val graph = navController.createGraph(startDestination = "ignored") {
            fragment<Fragment>("ignored")
        }.apply {
            nodes.clear()
            addDestination(routelessDestination)
            setStartDestination(routelessDestinationId)
        }
        return graph
    }

    private class BrokenNavActivity : FragmentActivity() {
        override fun getSupportFragmentManager(): androidx.fragment.app.FragmentManager {
            throw RuntimeException("Broken fragment manager")
        }
    }
}
