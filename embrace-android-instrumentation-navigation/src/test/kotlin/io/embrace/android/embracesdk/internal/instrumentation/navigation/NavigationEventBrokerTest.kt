package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
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

@RunWith(AndroidJUnit4::class)
internal class NavigationEventBrokerTest {
    private lateinit var clock: FakeClock
    private lateinit var loadTimes: MutableList<Long>
    private lateinit var states: MutableList<Pair<Long, Screen>>
    private lateinit var homeActivity: Activity
    private lateinit var settingsActivity: Activity
    private lateinit var profileActivity: Activity
    private lateinit var broker: NavigationEventBroker

    @Before
    fun setUp() {
        clock = FakeClock()
        loadTimes = mutableListOf()
        states = mutableListOf()
        broker = NavigationEventBroker { loadTimeMs, newScreen ->
            states.add(Pair(loadTimeMs, newScreen))
        }
        homeActivity = Robolectric.buildActivity(HomeActivity::class.java).get()
        settingsActivity = Robolectric.buildActivity(SettingsActivity::class.java).get()
        profileActivity = Robolectric.buildActivity(ProfileActivity::class.java).get()
    }

    @Test
    fun `start time of activity used instead of resume time when notifying screen load`() {
        loadTimes.add(broker.submitAndTick(ActivityStarted(homeActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        broker.submitAndTick(ActivityPaused(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(settingsActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(settingsActivity, clock.now()))
        broker.submitAndTick(ActivityPaused(settingsActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        loadTimes.add(broker.submitAndTick(ActivityStarted(profileActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(profileActivity, clock.now()))
        assertStateTransitions(
            listOf(
                homeActivity.screen(),
                settingsActivity.screen(),
                Screen.Backgrounded,
                profileActivity.screen(),
            ),
        )
    }

    @Test
    fun `duplicate events are dropped`() {
        broker.submitAndTick(ActivityStarted(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(homeActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        broker.submitAndTick(ActivityPaused(homeActivity, clock.now()))
        broker.submitAndTick(ActivityPaused(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        broker.submitAndTick(Backgrounded(clock.now()))
        assertStateTransitions(
            listOf(
                homeActivity.screen(),
                Screen.Backgrounded,
            ),
        )
    }

    @Test
    fun `no screen load recorded for activities that do not hit resume`() {
        broker.submitAndTick(ActivityStarted(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(settingsActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(settingsActivity, clock.now()))
        assertStateTransitions(listOf(settingsActivity.screen()))
    }

    @Test
    fun `multiple visible activities results in last activity to open being used as state`() {
        loadTimes.add(broker.submitAndTick(ActivityStarted(homeActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        broker.submitAndTick(ActivityStarted(settingsActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(settingsActivity, clock.now())))
        assertStateTransitions(listOf(homeActivity.screen(), settingsActivity.screen()))
    }

    @Test
    fun `multiple activities with interleaved callbacks results each resume causing a state value change`() {
        broker.submitAndTick(ActivityStarted(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(settingsActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(settingsActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(homeActivity, clock.now())))
        assertStateTransitions(
            listOf(settingsActivity.screen(), homeActivity.screen()),
        )
    }

    @Test
    fun `activity resume does not emit state update for NavController activity on first start`() {
        loadTimes.add(broker.simulateActivityStartWithNavController(homeActivity, "home"))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        assertStateTransitions(listOf(Screen.Named("home")))
    }

    @Test
    fun `NavController destination ignored if that activity is not visible when another activity becomes visible`() {
        loadTimes.add(broker.simulateActivityStartWithNavController(homeActivity, "home"))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        broker.submitAndTick(ActivityPaused(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(settingsActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(settingsActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        assertStateTransitions(listOf(Screen.Named("home"), settingsActivity.screen(), Screen.Backgrounded))
    }

    @Test
    fun `NavController destination change updates state with destination name`() {
        loadTimes.add(broker.simulateActivityStartWithNavController(homeActivity, "home"))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(NavControllerDestinationChanged(homeActivity, "about", clock.now())))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        assertStateTransitions(listOf(Screen.Named("home"), Screen.Named("about"), Screen.Backgrounded))
    }

    @Test
    fun `NavController destination named like a system value is still considered an app screen`() {
        loadTimes.add(broker.simulateActivityStartWithNavController(homeActivity, "home"))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        loadTimes.add(
            broker.submitAndTick(
                NavControllerDestinationChanged(
                    activity = homeActivity,
                    screenName = Screen.Backgrounded.toString(),
                    timestampMs = clock.now(),
                ),
            ),
        )
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        assertStateTransitions(
            listOf(
                Screen.Named("home"),
                Screen.Backgrounded,
                Screen.Named(Screen.Backgrounded.toString()),
                Screen.Backgrounded,
            ),
        )
    }

    @Test
    fun `NavController transitions properly to a destination named like the nav controller initializing system value`() {
        broker.submitAndTick(NavControllerAttached(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(homeActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        broker.submitAndTick(ActivityPaused(homeActivity, clock.now()))
        loadTimes.add(
            broker.submitAndTick(
                NavControllerDestinationChanged(
                    activity = homeActivity,
                    screenName = Screen.NavControllerInitializing.toString(),
                    timestampMs = clock.now(),
                ),
            ),
        )
        broker.submitAndTick(ActivityPaused(homeActivity, clock.now()))
        broker.submitAndTick(NavControllerAttached(settingsActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(settingsActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(settingsActivity, clock.now()))
        broker.submitAndTick(ActivityPaused(settingsActivity, clock.now()))
        assertStateTransitions(
            listOf(
                Screen.NavControllerInitializing,
                Screen.Named(Screen.NavControllerInitializing.toString()),
                Screen.NavControllerInitializing,
            ),
        )
    }

    @Test
    fun `interleaved activity and NavController events from different activities results in compound state value`() {
        loadTimes.add(broker.simulateActivityStartWithNavController(homeActivity, "home"))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        broker.submitAndTick(ActivityStarted(settingsActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(settingsActivity, clock.now())))
        loadTimes.add(broker.submitAndTick(NavControllerDestinationChanged(homeActivity, "about", clock.now())))
        assertStateTransitions(listOf(Screen.Named("home"), settingsActivity.screen(), Screen.Named("about")))
    }

    @Test
    fun `NavController activity returning from background re-emits last loaded destination`() {
        loadTimes.add(broker.simulateActivityStartWithNavController(homeActivity, "home"))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        broker.submitAndTick(ActivityPaused(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))

        loadTimes.add(broker.submitAndTick(ActivityStarted(homeActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(NavControllerDestinationChanged(homeActivity, "settings", clock.now())))
        broker.submitAndTick(ActivityPaused(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))

        loadTimes.add(broker.submitAndTick(ActivityStarted(homeActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))

        assertStateTransitions(
            listOf(
                Screen.Named("home"),
                Screen.Backgrounded,
                Screen.Named("home"),
                Screen.Named("settings"),
                Screen.Backgrounded,
                Screen.Named("settings"),
            ),
        )
    }

    /**
     * Simulates the event sequence produced by [ActivityNavigationTracker.handleActivityStarted]
     * for an Activity with a NavController: Attached → DestinationChanged → ActivityStarted.
     * Returns the timestamp of the DestinationChanged event (the screen load time).
     */
    private fun NavigationEventBroker.simulateActivityStartWithNavController(
        activity: Activity,
        destination: String,
    ): Long {
        submitAndTick(NavControllerAttached(activity, clock.now()))
        val destinationChangeTime =
            submitAndTick(NavControllerDestinationChanged(activity, destination, clock.now()))
        submitAndTick(ActivityStarted(activity, clock.now()))
        return destinationChangeTime
    }

    /**
     * Submits an event to the broker and advances the clock.
     * Returns the event's timestamp (the time before the tick).
     */
    private fun NavigationEventBroker.submitAndTick(event: NavigationEvent): Long {
        val eventTime = event.timestampMs
        onEvent(event)
        clock.tick()
        return eventTime
    }

    private fun Activity.screen(): Screen = Screen.Named(localClassName)

    private fun assertStateTransitions(expectedStates: List<Screen>) {
        val expectedTransitions = expectedStates.size
        assertEquals(expectedTransitions, states.size)
        (0..<expectedTransitions).forEach { i ->
            assertEquals(loadTimes[i], states[i].first)
            assertEquals(expectedStates[i], states[i].second)
        }
    }

    private class HomeActivity : Activity()
    private class SettingsActivity : Activity()
    private class ProfileActivity : Activity()
}
