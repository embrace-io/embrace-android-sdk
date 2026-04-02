package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.os.Handler
import android.os.Looper
import io.embrace.android.embracesdk.internal.clock.Clock
import java.util.concurrent.atomic.AtomicReference

/**
 * Receives instance of [NavigationEvent] from various sources, processes them serially, and updates
 * [NavigationStateDataSource] appropriately when a state change is detected.
 */
internal class NavigationEventBroker(
    looper: Looper = Looper.getMainLooper(),
    private val clock: Clock,
    private val onScreenLoad: (loadTimeMs: Long, newScreenName: String) -> Unit,
) {
    private val handler = Handler(looper)
    private val lastEvent = AtomicReference<NavigationEvent>(null)
    private val activityStartTimes = mutableMapOf<Int, Long>()
    private val visibleActivities = mutableMapOf<Int, String>()

    fun queueEvent(event: NavigationEvent) {
        handler.post {
            processEvent(event)
        }
    }

    private fun processEvent(event: NavigationEvent) {
        when (event) {
            is NavigationEvent.ActivityStarted -> {
                activityStartTimes[event.componentId] = clock.now()
            }
            is NavigationEvent.ActivityResumed -> {
                val eventTime = clock.now()
                activityStartTimes.remove(event.componentId)?.let { startTime ->
                    visibleActivities[event.componentId] = event.name
                    var loadTime = startTime
                    val stateValue = if (visibleActivities.values.size > 1) {
                        loadTime = eventTime
                        visibleActivities.values.toList().sorted().joinToString(separator = " + ")
                    } else {
                        event.name
                    }
                    notifyLoad(event, loadTime, stateValue)
                }
            }
            is NavigationEvent.ActivityPaused -> {
                visibleActivities.remove(event.componentId)
            }
            is NavigationEvent.Backgrounded -> notifyLoad(event)
        }
    }

    private fun notifyLoad(
        event: NavigationEvent,
        loadTime: Long = clock.now(),
        stateValue: String = event.name,
    ) {
        if (event != lastEvent.getAndSet(event)) {
            onScreenLoad(loadTime, stateValue)
        }
    }
}
