package io.embrace.android.embracesdk.internal.instrumentation.navigation

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.arch.navigation.CurrentScreen
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityDestroyed
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityPaused
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityResumed
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityStarted
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.Backgrounded
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenChanged
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenSourceAttached
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
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
        val eventBus = EventBus(FakeErrorHandler())
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
        assertStateTransitions(listOf(Screen.Named(HOME), Screen.Named(SETTINGS), Screen.Backgrounded, Screen.Named(PROFILE)))
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
        assertStateTransitions(listOf(Screen.Named(HOME), Screen.Backgrounded))
    }

    @Test
    fun `an activity resuming after a translucent activity closes is the screen again`() {
        loadTimes.add(broker.submitAndTick(ActivityStarted(HOME_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        // a translucent activity pauses home without stopping it, so home doesn't start again when it closes
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now()))
        broker.submitAndTick(ActivityPaused(SETTINGS_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now())))
        assertStateTransitions(listOf(Screen.Named(HOME), Screen.Named(SETTINGS), Screen.Named(HOME)))
    }

    @Test
    fun `no screen load recorded for activities that do not hit resume`() {
        broker.submitAndTick(ActivityStarted(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now()))
        assertStateTransitions(listOf(Screen.Named(SETTINGS)))
    }

    @Test
    fun `multiple visible activities results in last activity to open being used as state`() {
        loadTimes.add(broker.submitAndTick(ActivityStarted(HOME_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now())))
        assertStateTransitions(listOf(Screen.Named(HOME), Screen.Named(SETTINGS)))
    }

    @Test
    fun `multiple activities with interleaved callbacks results each resume causing a state value change`() {
        broker.submitAndTick(ActivityStarted(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now())))
        assertStateTransitions(listOf(Screen.Named(SETTINGS), Screen.Named(HOME)))
    }

    @Test
    fun `activity resume does not emit state update for an activity with a screen source on first start`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        assertStateTransitions(listOf(Screen.Named("home")))
    }

    @Test
    fun `screen change ignored if that activity is not visible when another activity becomes visible`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        assertStateTransitions(listOf(Screen.Named("home"), Screen.Named(SETTINGS), Screen.Backgrounded))
    }

    @Test
    fun `screen change updates state with the new screen name`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        loadTimes.add(broker.submitAndTick(ScreenChanged(HOME_ID, "about", clock.now())))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        assertStateTransitions(listOf(Screen.Named("home"), Screen.Named("about"), Screen.Backgrounded))
    }

    @Test
    fun `screen named like a system value is still considered an app screen`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        loadTimes.add(broker.submitAndTick(ScreenChanged(HOME_ID, Screen.Backgrounded.toString(), clock.now())))
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
    fun `screen source transitions properly to a screen named like the screen source initializing system value`() {
        broker.submitAndTick(ScreenSourceAttached(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(HOME_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        loadTimes.add(
            broker.submitAndTick(ScreenChanged(HOME_ID, Screen.NavControllerInitializing.toString(), clock.now())),
        )
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        broker.submitAndTick(ScreenSourceAttached(SETTINGS_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now()))
        broker.submitAndTick(ActivityPaused(SETTINGS_ID, clock.now()))
        assertStateTransitions(
            listOf(
                Screen.NavControllerInitializing,
                Screen.Named(Screen.NavControllerInitializing.toString()),
                Screen.NavControllerInitializing,
            ),
        )
    }

    @Test
    fun `interleaved activity and screen source signals from different activities results in compound state value`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityStarted(SETTINGS_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(SETTINGS_ID, SETTINGS, clock.now())))
        loadTimes.add(broker.submitAndTick(ScreenChanged(HOME_ID, "about", clock.now())))
        assertStateTransitions(listOf(Screen.Named("home"), Screen.Named(SETTINGS), Screen.Named("about")))
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

    @Test
    fun `a screen change while the app is in the background is shown when its activity resumes`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))

        // as when an app shows a lock screen as it goes to the background
        broker.submitAndTick(ScreenChanged(HOME_ID, "lock", clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now())))
        assertStateTransitions(listOf(Screen.Named("home"), Screen.Backgrounded, Screen.Named("lock")))
    }

    @Test
    fun `a screen change while another app's activity covers it is shown when the activity resumes`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        loadTimes.add(broker.submitAndTick(Backgrounded(clock.now())))
        loadTimes.add(broker.submitAndTick(ActivityStarted(HOME_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))

        // as when a permission prompt, shown by another app, pauses it and the result navigates before it resumes
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        broker.submitAndTick(ScreenChanged(HOME_ID, "camera", clock.now()))
        loadTimes.add(broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now())))
        assertStateTransitions(
            listOf(Screen.Named("home"), Screen.Backgrounded, Screen.Named("home"), Screen.Named("camera")),
        )
    }

    @Test
    fun `a destroyed activity's screen is not applied to a later activity with the same id`() {
        loadTimes.add(broker.simulateActivityStartWithScreenSource(HOME_ID, "home"))
        broker.submitAndTick(ActivityResumed(HOME_ID, HOME, clock.now()))
        broker.submitAndTick(ActivityPaused(HOME_ID, clock.now()))
        broker.submitAndTick(ActivityDestroyed(HOME_ID, clock.now()))

        // ids are identity hash codes, which a later Activity can reuse once the destroyed one is collected
        loadTimes.add(broker.submitAndTick(ActivityStarted(HOME_ID, clock.now())))
        broker.submitAndTick(ActivityResumed(HOME_ID, SETTINGS, clock.now()))
        assertStateTransitions(listOf(Screen.Named("home"), Screen.Named(SETTINGS)))
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

    private fun assertStateTransitions(expectedScreens: List<Screen>) {
        assertEquals(expectedScreens.size, screens.size)
        expectedScreens.forEachIndexed { index, expected ->
            assertEquals(loadTimes[index], screens[index].sinceMs)
            assertEquals(expected, screens[index].screen)
        }
    }

    private companion object {
        const val HOME_ID = 1
        const val SETTINGS_ID = 2
        const val PROFILE_ID = 3
        const val HOME = "HomeActivity"
        const val SETTINGS = "SettingsActivity"
        const val PROFILE = "ProfileActivity"
    }
}
