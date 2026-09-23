package io.embrace.android.embracesdk.internal.instrumentation.navigation

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.internal.arch.navigation.CurrentScreen
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityPaused
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityResumed
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityStarted
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.Backgrounded
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenChanged
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenSourceAttached
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The signals are driven here with no UI framework behind them, which is the claim the vocabulary rests on: a screen
 * source that is not androidx-navigation drives the broker identically.
 */
internal class NavigationEventBrokerTest {
    private lateinit var clock: FakeClock
    private lateinit var loadTimes: MutableList<Long>
    private lateinit var screens: MutableList<CurrentScreen>
    private lateinit var broker: NavigationEventBroker

    @Before
    fun setUp() {
        clock = FakeClock()
        loadTimes = mutableListOf()
        screens = mutableListOf()
        val eventBus = EventBus(FakeInternalLogger())
        eventBus.addStateHandler(CurrentScreen.KEY) { screens.add(it) }
        broker = NavigationEventBroker(eventBus)
    }

    @Test
    fun `start time of activity used instead of resume time when notifying screen load`() {
        loadTimes.add(broker.submitAndTick(ActivityStarted(HOME_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now()))
        broker.submitAndTick(ActivityPaused(SETTINGS_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        loadTimes.add(broker.submitAndTick(ActivityStarted(PROFILE_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(PROFILE_ID, PROFILE, clock.now()))
        assertStateTransitions(listOf(HOME, SETTINGS, BACKGROUNDED, PROFILE))
    }

    @Test
    fun `duplicate events are dropped`() {
        broker.submitAndTick(ActivityStarted(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(HOME_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        broker.submitAndTick(Backgrounded(clock.now()))
        assertStateTransitions(listOf(HOME, BACKGROUNDED))
    }

    @Test
    fun `no screen load recorded for activities that do not hit resume`() {
        broker.submitAndTick(ActivityStarted(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now()))
        assertStateTransitions(listOf(SETTINGS))
    }

    @Test
    fun `multiple visible activities results in last activity to open being used as state`() {
        loadTimes.add(broker.submitAndTick(ActivityStarted(HOME_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now())))
        assertStateTransitions(listOf(HOME, SETTINGS))
    }

    @Test
    fun `multiple activities with interleaved callbacks results each resume causing a state value change`() {
        broker.submitAndTick(ActivityStarted(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now())))
        assertStateTransitions(listOf(SETTINGS, HOME))
    }

    @Test
    fun `activity resume does not emit state update for an activity with a screen source on first start`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        assertStateTransitions(listOf("home"))
    }

    @Test
    fun `screen change ignored if that activity is not visible when another activity becomes visible`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        assertStateTransitions(listOf("home", SETTINGS, BACKGROUNDED))
    }

    @Test
    fun `screen change updates state with the new screen name`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        loadTimes.add(broker.submitAndTick(ScreenChanged(HOME_ID, "about", clock.now())))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        assertStateTransitions(listOf("home", "about", BACKGROUNDED))
    }

    @Test
    fun `interleaved activity and screen source signals from different activities results in compound state value`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now())))
        loadTimes.add(broker.submitAndTick(ScreenChanged(HOME_ID, "about", clock.now())))
        assertStateTransitions(listOf("home", SETTINGS, "about"))
    }

    @Test
    fun `activity with a screen source returning from background re-emits its last screen`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))

        loadTimes.add(broker.submitAndTick(ActivityStarted(HOME_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        loadTimes.add(broker.submitAndTick(ScreenChanged(HOME_ID, "settings", clock.now())))
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))

        loadTimes.add(broker.submitAndTick(ActivityStarted(HOME_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))

        assertStateTransitions(listOf("home", BACKGROUNDED, "home", "settings", BACKGROUNDED, "settings"))
    }

    /**
     * Drives the fixture's ordering for an Activity whose screens are named by a source: attached, screen change, then
     * started. Returns the timestamp of the screen change, which is the screen load time.
     *
     * The real tracker attaches a source when the Activity resumes, i.e. after ActivityResumed has been emitted, so this
     * ordering is the fixture's own; whether the real order is a bug is a separate question.
     */
    private fun NavigationEventBroker.simulateActivityStartWithScreenSource(
        instanceId: Int,
        screenName: String,
    ): Long {
        submitAndTick(ScreenSourceAttached(instanceId, clock.now()))
        val screenChangeTime = submitAndTick(ScreenChanged(instanceId, screenName, clock.now()))
        submitAndTick(ActivityStarted(instanceId, clock.now()))
        return screenChangeTime
    }

    /**
     * Submits a signal to the broker and advances the clock.
     * Returns the signal's timestamp (the time before the tick).
     */
    private fun NavigationEventBroker.submitAndTick(signal: NavigationSignal): Long {
        val signalTime = signal.timestampMs
        onEvent(signal)
        clock.tick()
        return signalTime
    }

    private fun assertStateTransitions(expectedScreens: List<String>) {
        assertEquals(expectedScreens.size, screens.size)
        expectedScreens.forEachIndexed { index, expected ->
            assertEquals(loadTimes[index], screens[index].sinceMs)
            assertEquals(expected, screens[index].name)
        }
    }

    private companion object {
        const val HOME_ID = 1
        const val SETTINGS_ID = 2
        const val PROFILE_ID = 3
        const val HOME = "HomeActivity"
        const val SETTINGS = "SettingsActivity"
        const val PROFILE = "ProfileActivity"
        const val BACKGROUNDED = "Backgrounded"
    }
}
