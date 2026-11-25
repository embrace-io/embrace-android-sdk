package io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal

import android.app.Activity
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.arch.navigation.getId
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource.ACTIVITY
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource.NAVIGATION
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStart
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop.Reason.HIDDEN
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop.Reason.NAVIGATED
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.utils.event.EventHandler
import io.embrace.android.embracesdk.internal.utils.event.EventKey
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class NavigationScreenSourceTest {

    /**
     * Starts and stops recorded into one list, so that their order is asserted.
     */
    private sealed interface Emitted {
        data class Start(val name: String, val source: ScreenSource, val activityId: Int) : Emitted
        data class Stop(val source: ScreenSource, val activityId: Int, val reason: ScreenStop.Reason) : Emitted
    }

    private lateinit var clock: FakeClock
    private lateinit var eventBus: EventBus
    private lateinit var emitted: MutableList<Emitted>
    private lateinit var source: NavigationScreenSource
    private lateinit var first: Activity
    private lateinit var second: Activity

    @Before
    fun setUp() {
        clock = FakeClock()
        eventBus = EventBus(FakeErrorHandler())
        emitted = mutableListOf()
        eventBus.addHandler(ScreenStart.KEY) { emitted.add(Emitted.Start(it.name, it.source, it.activityId)) }
        eventBus.addHandler(ScreenStop.KEY) { emitted.add(Emitted.Stop(it.source, it.activityId, it.reason)) }
        source = NavigationScreenSource(eventBus, clock).apply { subscribe() }
        first = Activity()
        second = Activity()
    }

    @Test
    fun `a destination change of a resumed activity stops the open destination then starts the new one`() {
        resume(first)
        source.onDestinationChanged(first.getId(), "Home", clock.now())
        source.onDestinationChanged(first.getId(), "Settings", clock.now())
        assertEquals(
            listOf(
                Emitted.Start("Home", NAVIGATION, first.getId()),
                Emitted.Stop(NAVIGATION, first.getId(), NAVIGATED),
                Emitted.Start("Settings", NAVIGATION, first.getId()),
            ),
            emitted,
        )
    }

    @Test
    fun `a destination known before its activity resumes starts when it resumes`() {
        source.onDestinationChanged(first.getId(), "Home", clock.now())
        assertEquals(emptyList<Emitted>(), emitted)
        resume(first)
        assertEquals(listOf(Emitted.Start("Home", NAVIGATION, first.getId())), emitted)
    }

    @Test
    fun `pausing stops the open destination, and resuming starts the last one again`() {
        resume(first)
        source.onDestinationChanged(first.getId(), "Home", clock.now())
        pause(first)
        source.onDestinationChanged(first.getId(), "Settings", clock.now())
        resume(first)
        assertEquals(
            listOf(
                Emitted.Start("Home", NAVIGATION, first.getId()),
                Emitted.Stop(NAVIGATION, first.getId(), HIDDEN),
                Emitted.Start("Settings", NAVIGATION, first.getId()),
            ),
            emitted,
        )
    }

    @Test
    fun `destinations start after and stop before their activity, on the main and on the pre and post events`() {
        val activitySource = ActivityStandIn(eventBus).apply { subscribe() }
        resume(first)
        source.onDestinationChanged(first.getId(), "Home", clock.now())
        pause(first)
        resume(first)
        assertEquals(
            listOf(
                // main events: the activity on the default order, the destination after it on resume and before it on pause
                Emitted.Start("Activity", ACTIVITY, first.getId()),
                Emitted.Start("Home", NAVIGATION, first.getId()),
                Emitted.Stop(NAVIGATION, first.getId(), HIDDEN),
                Emitted.Stop(ACTIVITY, first.getId(), HIDDEN),
                Emitted.Start("Activity", ACTIVITY, first.getId()),
                Emitted.Start("Home", NAVIGATION, first.getId()),
            ),
            emitted,
        )
        activitySource.close()

        // pre and post events: PRE_RESUMED precedes RESUMED, and POST_PAUSED follows PAUSED
        emitted.clear()
        ActivityStandIn(eventBus, ActivityLifecycle.PRE_RESUMED, ActivityLifecycle.POST_PAUSED).subscribe()
        resume(second)
        source.onDestinationChanged(second.getId(), "Home", clock.now())
        pause(second)
        assertEquals(
            listOf(
                Emitted.Start("Activity", ACTIVITY, second.getId()),
                Emitted.Start("Home", NAVIGATION, second.getId()),
                Emitted.Stop(NAVIGATION, second.getId(), HIDDEN),
                Emitted.Stop(ACTIVITY, second.getId(), HIDDEN),
            ),
            emitted,
        )
    }

    @Test
    fun `destinations of different activities are independent`() {
        resume(first)
        source.onDestinationChanged(first.getId(), "Home", clock.now())
        source.onDestinationChanged(second.getId(), "Other", clock.now())
        pause(first)
        resume(second)
        assertEquals(
            listOf(
                Emitted.Start("Home", NAVIGATION, first.getId()),
                Emitted.Stop(NAVIGATION, first.getId(), HIDDEN),
                Emitted.Start("Other", NAVIGATION, second.getId()),
            ),
            emitted,
        )
    }

    @Test
    fun `a destroyed activity's destination is forgotten`() {
        source.onDestinationChanged(first.getId(), "Home", clock.now())
        eventBus.emit(ActivityLifecycle.DESTROYED, first)
        resume(first)
        assertEquals(emptyList<Emitted>(), emitted)
    }

    @Test
    fun `nothing is emitted for lifecycle events after close`() {
        source.onDestinationChanged(first.getId(), "Home", clock.now())
        source.close()
        resume(first)
        assertEquals(emptyList<Emitted>(), emitted)
    }

    private fun resume(activity: Activity) {
        eventBus.emit(ActivityLifecycle.PRE_RESUMED, activity)
        eventBus.emit(ActivityLifecycle.RESUMED, activity)
        eventBus.emit(ActivityLifecycle.POST_RESUMED, activity)
    }

    private fun pause(activity: Activity) {
        eventBus.emit(ActivityLifecycle.PRE_PAUSED, activity)
        eventBus.emit(ActivityLifecycle.PAUSED, activity)
        eventBus.emit(ActivityLifecycle.POST_PAUSED, activity)
    }

    /**
     * Stands in for the Activity screen source, which lives in another module: starts on [resumedKey] and stops on
     * [pausedKey], at the default order.
     */
    private class ActivityStandIn(
        private val eventBus: EventBus,
        private val resumedKey: EventKey<Activity> = ActivityLifecycle.RESUMED,
        private val pausedKey: EventKey<Activity> = ActivityLifecycle.PAUSED,
    ) : AutoCloseable {
        private val onResumed = EventHandler<Activity> {
            eventBus.emit(ScreenStart.KEY, ScreenStart("Activity", ACTIVITY, it.getId(), 0))
        }
        private val onPaused = EventHandler<Activity> {
            eventBus.emit(ScreenStop.KEY, ScreenStop(ACTIVITY, it.getId(), 0, HIDDEN))
        }

        fun subscribe() {
            eventBus.addHandler(resumedKey, onResumed)
            eventBus.addHandler(pausedKey, onPaused)
        }

        override fun close() {
            eventBus.removeHandler(resumedKey, onResumed)
            eventBus.removeHandler(pausedKey, onPaused)
        }
    }
}
