package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.os.Build
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeNavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityPaused
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityResumed
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityStarted
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.Backgrounded
import io.embrace.android.embracesdk.internal.arch.navigation.getId
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class ActivityNavigationTrackerTest {

    private lateinit var clock: FakeClock
    private lateinit var eventBus: EventBus
    private lateinit var signals: MutableList<NavigationSignal>
    private lateinit var navigationTrackingService: FakeNavigationTrackingService
    private lateinit var application: FakeApplication
    private lateinit var first: Activity
    private lateinit var second: Activity

    @Before
    fun setUp() {
        clock = FakeClock()
        signals = mutableListOf()
        eventBus = EventBus(FakeErrorHandler())
        eventBus.addHandler(NavigationSignal.KEY) { signals.add(it) }
        navigationTrackingService = FakeNavigationTrackingService()
        application = FakeApplication()
        first = DopeActivity()
        second = CoolActivity()
    }

    @Test
    fun `activity transitions produce the right signals in P`() {
        createTracker(Build.VERSION_CODES.P).assertPlainActivityNavigation()
    }

    @Test
    fun `activity transitions produce the right signals in Q`() {
        createTracker(Build.VERSION_CODES.Q).assertPlainActivityNavigation()
    }

    @Test
    fun `concurrent activities produce the right signals in P`() {
        createTracker(Build.VERSION_CODES.P).assertConcurrentPlainActivityNavigation()
    }

    @Test
    fun `concurrent activities produce the right signals in Q`() {
        createTracker(Build.VERSION_CODES.Q).assertConcurrentPlainActivityNavigation()
    }

    private fun ActivityNavigationTracker.assertPlainActivityNavigation() {
        val times = transitionBetweenActivities()
        assertEquals(7, times.size)
        assertSignals(
            ActivityStarted(first.getId(), times[0]),
            ActivityResumed(first.getId(), first.localClassName, times[1]),
            ActivityPaused(first.getId(), times[2]),
            ActivityStarted(second.getId(), times[3]),
            ActivityResumed(second.getId(), second.localClassName, times[4]),
            ActivityPaused(second.getId(), times[5]),
            Backgrounded(times[6]),
        )
        // each resumed Activity is offered to the screen source, after its own resume signal
        assertEquals(listOf<Activity>(first, second), navigationTrackingService.trackedActivities)
    }

    private fun ActivityNavigationTracker.assertConcurrentPlainActivityNavigation() {
        val times = openConcurrentActivities()
        assertEquals(7, times.size)
        assertSignals(
            ActivityStarted(first.getId(), times[0]),
            ActivityResumed(first.getId(), first.localClassName, times[1]),
            ActivityStarted(second.getId(), times[2]),
            ActivityResumed(second.getId(), second.localClassName, times[3]),
            ActivityPaused(first.getId(), times[4]),
            ActivityPaused(second.getId(), times[5]),
            Backgrounded(times[6]),
        )
    }

    private fun ActivityNavigationTracker.transitionBetweenActivities(): List<Long> = with(application) {
        invokeCallbacks(
            listOf(
                { start(first) },
                { resume(first) },
                { pause(first) },
                { start(second) },
                { resume(second) },
                { pause(second) },
                ::onBackground,
            ),
        )
    }

    private fun ActivityNavigationTracker.openConcurrentActivities(): List<Long> = with(application) {
        invokeCallbacks(
            listOf(
                { start(first) },
                { resume(first) },
                { start(second) },
                { resume(second) },
                { pause(first) },
                { pause(second) },
                ::onBackground,
            ),
        )
    }

    private fun invokeCallbacks(callbacks: List<() -> Unit>): List<Long> {
        val times = mutableListOf<Long>()
        callbacks.forEach {
            times.add(clock.tick())
            it()
        }
        return times
    }

    private fun createTracker(sdkInt: Int): ActivityNavigationTracker {
        return ActivityNavigationTracker(
            clock,
            eventBus,
            navigationTrackingService,
            FakeErrorHandler(),
            VersionChecker { min -> sdkInt >= min },
        ).apply {
            application.registerActivityLifecycleCallbacks(this)
        }
    }

    private fun assertSignals(vararg expected: NavigationSignal) {
        assertEquals(expected.toList(), signals)
    }

    private class DopeActivity : Activity() {
        override fun getLocalClassName(): String = "DopeActivity"
    }

    private class CoolActivity : Activity() {
        override fun getLocalClassName(): String = "CoolActivity"
    }
}
