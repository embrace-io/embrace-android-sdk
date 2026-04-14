package io.embrace.android.embracesdk.internal.instrumentation.navigation

import androidx.annotation.UiThread
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
    private val visibleScreens = mutableMapOf<Int, String>()
    private val lastNavControllerDestinations = mutableMapOf<Int, String>()

    @UiThread
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
                    // If the activity doesn't have a NavController, set the activity name as the activity's visible screen
                    // and update the destination based on what screens are visible
                    if (!lastNavControllerDestinations.contains(event.componentId)) {
                        visibleScreens[event.componentId] = event.name
                        calculateStateAndNotifyLoad(
                            activityStartTime = startTime,
                            eventTime = event.timestampMs,
                            event = event
                        )
                    } else if (!visibleScreens.contains(event.componentId)) {
                        // If the activity has a NavController but there isn't a screen visible, the app is emerging from the background.
                        // So we make the last destination visible and notify about the screen load, using the start time as the event time.
                        lastNavControllerDestinations[event.componentId]?.let { lastDest ->
                            visibleScreens[event.componentId] = lastDest
                            notifyLoad(
                                event = event,
                                loadTime = startTime,
                                stateValue = lastDest
                            )
                        }
                    }
                }
            }
            is NavigationEvent.ActivityPaused -> {
                visibleScreens.remove(event.componentId)
            }
            is NavigationEvent.NavControllerAttached -> {
                lastNavControllerDestinations[event.componentId] = NAV_CONTROLLER_INIT
            }
            is NavigationEvent.NavControllerDestinationChanged -> {
                lastNavControllerDestinations[event.componentId] = event.name
                visibleScreens[event.componentId] = event.name
                calculateStateAndNotifyLoad(
                    eventTime = event.timestampMs,
                    event = event
                )
            }
            is NavigationEvent.Backgrounded -> {
                notifyLoad(event)
            }
        }
    }

    private fun calculateStateAndNotifyLoad(
        activityStartTime: Long? = null,
        eventTime: Long,
        event: NavigationEvent,
    ) {
        var loadTime = activityStartTime ?: eventTime
        if (visibleScreens.values.size > 1) {
            loadTime = eventTime
        }
        notifyLoad(event, loadTime)
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

    private companion object {
        // A state where the NavController is attached but the default destination has not been loaded, which should be rare
        // as a destination update to the default is fired synchronously as the controller attaches.
        const val NAV_CONTROLLER_INIT = "NavController Initializing"
    }
}
