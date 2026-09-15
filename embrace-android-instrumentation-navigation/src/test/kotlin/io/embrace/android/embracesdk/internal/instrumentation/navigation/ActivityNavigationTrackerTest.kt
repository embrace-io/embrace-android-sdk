package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeInternalLogger
import io.embrace.android.embracesdk.fakes.FakeNavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityPaused
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityResumed
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ActivityStarted
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.Backgrounded
import io.embrace.android.embracesdk.internal.arch.navigation.getId
import io.embrace.android.embracesdk.internal.utils.event.EventBus
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
    private lateinit var eventBus: EventBus
    private lateinit var signals: MutableList<NavigationSignal>
    private lateinit var navigationTrackingService: FakeNavigationTrackingService
    private lateinit var activityController: ActivityController<DopeActivity>
    private lateinit var anotherController: ActivityController<CoolActivity>

    @Before
    fun setUp() {
        clock = FakeClock()
        signals = mutableListOf()
        eventBus = EventBus(FakeInternalLogger())
        eventBus.addHandler<NavigationSignal> { signals.add(it) }
        navigationTrackingService = FakeNavigationTrackingService()
        activityController = Robolectric.buildActivity(DopeActivity::class.java).create()
        anotherController = Robolectric.buildActivity(CoolActivity::class.java).create()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `activity transitions produce the right signals in P`() {
        createTracker().assertPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `activity transitions produce the right signals in Q`() {
        createTracker().assertPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `concurrent activities produce the right signals in P`() {
        createTracker().assertConcurrentPlainActivityNavigation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `concurrent activities produce the right signals in Q`() {
        createTracker().assertConcurrentPlainActivityNavigation()
    }

    private fun ActivityNavigationTracker.assertPlainActivityNavigation() {
        val times = transitionBetweenActivities(activityController, anotherController)
        val first = activityController.get()
        val second = anotherController.get()
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
        val times = openConcurrentActivities(activityController, anotherController)
        val first = activityController.get()
        val second = anotherController.get()
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

    private fun ActivityNavigationTracker.transitionBetweenActivities(
        first: ActivityController<out Activity>,
        second: ActivityController<out Activity>,
    ): List<Long> {
        return invokeCallbacks(
            listOf(first::start, first::resume, first::pause, second::start, second::resume, second::pause, ::onBackground),
        )
    }

    private fun ActivityNavigationTracker.openConcurrentActivities(
        first: ActivityController<out Activity>,
        second: ActivityController<out Activity>,
    ): List<Long> {
        return invokeCallbacks(
            listOf(first::start, first::resume, second::start, second::resume, first::pause, second::pause, ::onBackground),
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

    private fun createTracker(): ActivityNavigationTracker {
        return ActivityNavigationTracker(clock, eventBus, navigationTrackingService).apply {
            RuntimeEnvironment.getApplication().registerActivityLifecycleCallbacks(this)
        }
    }

    private fun assertSignals(vararg expected: NavigationSignal) {
        assertEquals(expected.toList(), signals)
    }

    private class DopeActivity : Activity()
    private class CoolActivity : Activity()
}
