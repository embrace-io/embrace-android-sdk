package io.embrace.android.embracesdk.internal.instrumentation.navigation

import java.util.concurrent.atomic.AtomicReference

/**
 * Receives instance of [NavigationEvent] from various sources, processes them serially, and updates [NavigationStateDataSource]
 * appropriately when a state change is detected. The timing of the events will be provided by the event themselves so the
 * broker only needs to hand them off to be processed in order.
 */
internal class NavigationEventBroker(
    private val onScreenLoad: (loadTimeMs: Long, newScreenName: String) -> Unit,
) {
    private val lastEvent = AtomicReference<NavigationEvent?>(null)
    private val activityStartTimes = mutableMapOf<Int, Long>()
    private val visibleActivities = mutableMapOf<Int, String>()

    fun onEvent(event: NavigationEvent) {
        processEvent(event)
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
        val notify = lastEvent.getAndSet(event)?.let {
            it.componentId != event.componentId || it.name != event.name
        } ?: true

        if (notify) {
            onScreenLoad(loadTime, stateValue)
        }
    }
}
