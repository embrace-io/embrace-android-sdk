package io.embrace.android.embracesdk.internal.instrumentation.compose.navigation

import android.R.id.content
import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.createGraph
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeNavigationTrackingService
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
    private lateinit var fakeTracker: FakeNavigationTrackingService
    private lateinit var tracker: NavControllerTracker
    private lateinit var logger: FakeInternalLogger
    private lateinit var activity: FragmentActivity

    @Before
    fun setUp() {
        clock = FakeClock()
        fakeTracker = FakeNavigationTrackingService()
        logger = FakeInternalLogger()
        tracker = NavControllerTracker(fakeTracker, clock, logger)
        activity = createActivity()
    }

    @Test
    fun `tracking activity with nav controller produces attached and destination callbacks`() {
        tracker.trackNavigation(activity)
        assertEquals(1, fakeTracker.attachedCalls.size)
        assertEquals(1, fakeTracker.destinationChangedCalls.size)
        assertEquals("home", fakeTracker.destinationChangedCalls[0].screenName)
    }

    @Test
    fun `track on activity with no nav controller produces no callbacks`() {
        tracker.trackNavigation(buildActivity(Activity::class.java).setup().get())
        assertTrue(fakeTracker.attachedCalls.isEmpty())
    }

    @Test
    fun `track on FragmentActivity without NavHostFragment produces no callbacks`() {
        tracker.trackNavigation(buildActivity(FragmentActivity::class.java).setup().get())
        assertTrue(fakeTracker.attachedCalls.isEmpty())
    }

    @Test
    fun `calling track twice on same activity does not produce duplicate callbacks`() {
        tracker.trackNavigation(activity)
        tracker.trackNavigation(activity)
        assertEquals(1, fakeTracker.attachedCalls.size)
    }

    @Test
    fun `tracking different activity instances produces callbacks for each`() {
        val anotherActivity = createActivity()
        tracker.trackNavigation(activity)
        tracker.trackNavigation(anotherActivity)
        assertEquals(2, fakeTracker.attachedCalls.size)
        assertEquals(2, fakeTracker.destinationChangedCalls.size)
    }

    @Test
    fun `destination screen name falls back to label when route is null`() {
        val labelActivity = createActivity(::graphWithRoutelessDestinationWithLabel)
        tracker.trackNavigation(labelActivity)
        assertEquals("My Home", fakeTracker.destinationChangedCalls[0].screenName)
    }

    @Test
    fun `destination screen name falls back to navigatorName when route and label are null`() {
        val idActivity = createActivity(::graphWithDestinationWithoutRouteAndLabel)
        tracker.trackNavigation(idActivity)
        assertEquals("fragment", fakeTracker.destinationChangedCalls[0].screenName)
    }

    @Test
    fun `tracking NavController explicitly produces callbacks for the provided activity`() {
        tracker.trackNavigation(activity, createTestNavController())
        assertEquals(1, fakeTracker.attachedCalls.size)
        assertEquals("home", fakeTracker.destinationChangedCalls[0].screenName)
    }

    @Test
    fun `activity will always be associated with the first NavController it tracks`() {
        tracker.trackNavigation(activity, createTestNavController("foo"))
        tracker.trackNavigation(activity, createTestNavController("bar"))
        tracker.trackNavigation(activity)
        assertEquals(1, fakeTracker.attachedCalls.size)
        assertEquals("foo", fakeTracker.destinationChangedCalls[0].screenName)
    }

    @Test
    fun `explicit tracking will be ignored if activity has already been discovered`() {
        tracker.trackNavigation(activity)
        tracker.trackNavigation(activity, createTestNavController("foo"))
        assertEquals(1, fakeTracker.attachedCalls.size)
        assertEquals("home", fakeTracker.destinationChangedCalls[0].screenName)
    }

    @Test
    fun `error during tracking is logged and does not crash`() {
        val errorLogger = FakeInternalLogger(throwOnInternalError = false)
        tracker = NavControllerTracker(fakeTracker, clock, errorLogger)
        tracker.trackNavigation(buildActivity(BrokenNavActivity::class.java).setup().get())
        assertTrue(fakeTracker.attachedCalls.isEmpty())
        assertTrue(
            errorLogger.internalErrorMessages.any {
                it.msg == InternalErrorType.NAV_CONTROLLER_TRACKING_FAIL.toString()
            }
        )
    }

    private fun createActivity(
        navGraphProvider: (navController: NavController) -> NavGraph = { navController ->
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
        navHostFragment.navController.graph = navGraphProvider(navHostFragment.navController)
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
        return navController.createGraph(startDestination = "ignored") {
            fragment<Fragment>("ignored")
        }.apply {
            nodes.clear()
            addDestination(routelessDestination)
            setStartDestination(routelessDestinationId)
        }
    }

    private fun createTestNavController(startDestination: String = "home"): NavController =
        TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            val graphNavigator = navigatorProvider.getNavigator(NavGraphNavigator::class.java)
            graph = graphNavigator.createDestination().apply {
                addDestination(NavDestination("testNavDestination").apply { route = startDestination })
                setStartDestination(startDestination)
            }
        }

    private class BrokenNavActivity : FragmentActivity() {
        override fun getSupportFragmentManager(): androidx.fragment.app.FragmentManager {
            throw RuntimeException("Broken fragment manager")
        }
    }
}
