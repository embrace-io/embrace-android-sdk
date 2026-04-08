package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityPaused
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityResumed
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.ActivityStarted
import io.embrace.android.embracesdk.internal.instrumentation.navigation.NavigationEvent.Backgrounded
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
internal class NavigationEventBrokerTest {
    private lateinit var clock: FakeClock
    private lateinit var loadTimes: MutableList<Long>
    private lateinit var states: MutableList<Pair<Long, String>>
    private lateinit var homeActivity: Activity
    private lateinit var settingsActivity: Activity
    private lateinit var profileActivity: Activity
    private lateinit var broker: NavigationEventBroker

    @Before
    fun setUp() {
        clock = FakeClock()
        loadTimes = mutableListOf()
        states = mutableListOf()
        broker = NavigationEventBroker { loadTimeMs, newScreenName ->
            states.add(Pair(loadTimeMs, newScreenName))
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
        assertEquals(
            listOf(
                Pair(loadTimes[0], homeActivity.localClassName),
                Pair(loadTimes[1], settingsActivity.localClassName),
                Pair(loadTimes[2], "Backgrounded"),
                Pair(loadTimes[3], profileActivity.localClassName)
            ),
            states
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
        assertEquals(
            listOf(
                Pair(loadTimes[0], homeActivity.localClassName),
                Pair(loadTimes[1], "Backgrounded"),
            ),
            states
        )
    }

    @Test
    fun `no screen load recorded for activities that do not hit resume`() {
        broker.submitAndTick(ActivityStarted(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(settingsActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(settingsActivity, clock.now()))
        assertEquals(Pair(loadTimes[0], settingsActivity.localClassName), states.single())
    }

    @Test
    fun `multiple visible activities results in compound state`() {
        loadTimes.add(broker.submitAndTick(ActivityStarted(homeActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(homeActivity, clock.now()))
        broker.submitAndTick(ActivityStarted(settingsActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(settingsActivity, clock.now())))
        assertEquals(
            listOf(
                Pair(loadTimes[0], homeActivity.localClassName),
                Pair(loadTimes[1], "${homeActivity.localClassName} + ${settingsActivity.localClassName}")
            ),
            states
        )
    }

    @Test
    fun `multiple activities with interleaved activity callback orders results in compound state`() {
        broker.submitAndTick(ActivityStarted(homeActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(settingsActivity, clock.now())))
        broker.submitAndTick(ActivityResumed(settingsActivity, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(homeActivity, clock.now())))

        assertEquals(
            listOf(
                Pair(loadTimes[0], settingsActivity.localClassName),
                Pair(loadTimes[1], "${homeActivity.localClassName} + ${settingsActivity.localClassName}")
            ),
            states
        )
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

    private class HomeActivity : Activity()
    private class SettingsActivity : Activity()
    private class ProfileActivity : Activity()
}
