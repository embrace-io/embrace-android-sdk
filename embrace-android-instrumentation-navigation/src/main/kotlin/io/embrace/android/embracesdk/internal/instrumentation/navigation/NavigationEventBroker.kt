package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicReference

/**
 * Receives instance of [NavigationEvent] from various sources, processes them serially, and updates [NavigationStateDataSource]
 * appropriately when a state change is detected. The timing of the events will be provided by the event themselves so the
 * broker only needs to hand them off to be processed in order.
 */
internal class NavigationEventBroker(
    looper: Looper = Looper.getMainLooper(),
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
                activityStartTimes[event.componentId] = event.timestampMs
            }
            is NavigationEvent.ActivityResumed -> {
                activityStartTimes.remove(event.componentId)?.let { startTime ->
                    visibleActivities[event.componentId] = event.name
                    var loadTime = startTime
                    val stateValue = if (visibleActivities.values.size > 1) {
                        loadTime = event.timestampMs
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
        loadTime: Long = event.timestampMs,
        stateValue: String = event.name,
    ) {
        if (event != lastEvent.getAndSet(event)) {
            onScreenLoad(loadTime, stateValue)
        }
    }
}
