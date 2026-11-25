package io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.arch.navigation.getId
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStart
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.utils.event.EventHandler

/**
 * Starts and stops a screen for the navigation destination shown within each Activity, while that Activity is resumed.
 *
 * A destination change stops the open destination and starts the new one. The Activity pausing stops its open destination,
 * and resuming starts its last destination again. The resume is handled after, and the pause before, the default order on
 * the main events, so that on every API level a destination starts after its Activity's screen and stops before it.
 */
internal class NavigationScreenSource(
    private val eventBus: EventBus,
    private val clock: Clock,
) : AutoCloseable {

    private class ActivityDestinations {
        var lastName: String? = null
        var resumed = false
        var open = false
    }

    /**
     * Guarded by itself, as destinations can change on whichever thread the navigation library notifies on.
     */
    private val activities = HashMap<Int, ActivityDestinations>()

    private val onResumed = EventHandler<Activity> { activity ->
        val id = activity.getId()
        val name = synchronized(activities) {
            val destinations = activities.getOrPut(id, ::ActivityDestinations)
            destinations.resumed = true
            destinations.lastName?.takeUnless { destinations.open }?.also { destinations.open = true }
        }
        name?.let { start(id, it, clock.now()) }
    }

    private val onPaused = EventHandler<Activity> { activity ->
        val id = activity.getId()
        val wasOpen = synchronized(activities) {
            activities[id]?.run {
                resumed = false
                open.also { open = false }
            } ?: false
        }
        if (wasOpen) {
            stop(id, clock.now(), ScreenStop.Reason.HIDDEN)
        }
    }

    private val onDestroyed = EventHandler<Activity> { activity ->
        synchronized(activities) {
            activities.remove(activity.getId())
        }
    }

    /**
     * The destination of the Activity identified by [activityId] has changed to [name].
     */
    fun onDestinationChanged(activityId: Int, name: String, timestampMs: Long) {
        var wasOpen = false
        var resumed = false
        synchronized(activities) {
            val destinations = activities.getOrPut(activityId, ::ActivityDestinations)
            wasOpen = destinations.open
            resumed = destinations.resumed
            destinations.lastName = name
            destinations.open = resumed
        }
        if (wasOpen) {
            stop(activityId, timestampMs, ScreenStop.Reason.NAVIGATED)
        }
        if (resumed) {
            start(activityId, name, timestampMs)
        }
    }

    fun subscribe() {
        eventBus.addHandler(ActivityLifecycle.RESUMED, AFTER_ACTIVITY, onResumed)
        eventBus.addHandler(ActivityLifecycle.PAUSED, BEFORE_ACTIVITY, onPaused)
        eventBus.addHandler(ActivityLifecycle.DESTROYED, onDestroyed)
    }

    override fun close() {
        eventBus.removeHandler(ActivityLifecycle.RESUMED, onResumed)
        eventBus.removeHandler(ActivityLifecycle.PAUSED, onPaused)
        eventBus.removeHandler(ActivityLifecycle.DESTROYED, onDestroyed)
    }

    private fun start(activityId: Int, name: String, timestampMs: Long) {
        eventBus.emit(ScreenStart.KEY, ScreenStart(name, ScreenSource.NAVIGATION, activityId, timestampMs))
    }

    private fun stop(activityId: Int, timestampMs: Long, reason: ScreenStop.Reason) {
        eventBus.emit(ScreenStop.KEY, ScreenStop(ScreenSource.NAVIGATION, activityId, timestampMs, reason))
    }

    private companion object {
        const val AFTER_ACTIVITY = 1
        const val BEFORE_ACTIVITY = -1
    }
}
