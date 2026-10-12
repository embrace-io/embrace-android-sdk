package io.embrace.android.embracesdk.internal.instrumentation.navigation

import androidx.annotation.UiThread
import io.embrace.android.embracesdk.internal.arch.navigation.CurrentScreen
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.NavigationState.Screen
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.utils.event.EventHandler

/**
 * Resolves the [NavigationSignal]s emitted by every navigation source into the screen currently shown, publishing each
 * change against [CurrentScreen.KEY]. The signals carry their own timing, so the broker only has to process them in order.
 */
internal class NavigationEventBroker(
    private val eventBus: EventBus,
) : EventHandler<NavigationSignal> {

    private val activityStartTimes = mutableMapOf<Int, Long>()
    private val visibleScreens = mutableMapOf<Int, Screen>()
    private val lastScreenSourceScreens = mutableMapOf<Int, Screen>()

    private var lastLoadInstanceId = NO_INSTANCE
    private var lastLoadName: String? = null

    @UiThread
    override fun onEvent(event: NavigationSignal) {
        when (event) {
            is NavigationSignal.ActivityStarted -> {
                activityStartTimes[event.instanceId] = event.timestampMs
            }

            is NavigationSignal.ActivityResumed -> handleActivityResumed(event)

            is NavigationSignal.ActivityPaused -> {
                visibleScreens.remove(event.instanceId)
            }

            is NavigationSignal.ScreenSourceAttached -> {
                lastScreenSourceScreens[event.instanceId] = Screen.NavControllerInitializing
            }

            is NavigationSignal.ScreenChanged -> {
                val screen = Screen.Named(event.name)
                lastScreenSourceScreens[event.instanceId] = screen
                visibleScreens[event.instanceId] = screen
                calculateStateAndNotifyLoad(
                    eventTime = event.timestampMs,
                    instanceId = event.instanceId,
                    name = event.name,
                )
            }

            is NavigationSignal.Backgrounded -> notifyLoad(
                instanceId = NO_INSTANCE,
                name = Screen.Backgrounded.toString(),
                loadTime = event.timestampMs,
                screen = Screen.Backgrounded,
            )
        }
    }

    private fun handleActivityResumed(event: NavigationSignal.ActivityResumed) {
        val startTime = activityStartTimes.remove(event.instanceId) ?: return

        // If the Activity doesn't have a screen source, set the Activity name as the Activity's visible screen
        // and update the destination based on what screens are visible
        if (!lastScreenSourceScreens.contains(event.instanceId)) {
            visibleScreens[event.instanceId] = Screen.Named(event.name)
            calculateStateAndNotifyLoad(
                activityStartTime = startTime,
                eventTime = event.timestampMs,
                instanceId = event.instanceId,
                name = event.name,
            )
        } else if (!visibleScreens.contains(event.instanceId)) {
            // If the Activity has a screen source but there isn't a screen visible, the app is emerging from the background.
            // So we make the last screen visible and notify about the screen load, using the start time as the event time.
            lastScreenSourceScreens[event.instanceId]?.let { lastScreen ->
                visibleScreens[event.instanceId] = lastScreen
                notifyLoad(
                    instanceId = event.instanceId,
                    name = event.name,
                    loadTime = startTime,
                    screen = lastScreen,
                )
            }
        }
    }

    private fun calculateStateAndNotifyLoad(
        activityStartTime: Long? = null,
        eventTime: Long,
        instanceId: Int,
        name: String,
    ) {
        var loadTime = activityStartTime ?: eventTime
        if (visibleScreens.values.size > 1) {
            loadTime = eventTime
        }
        notifyLoad(instanceId = instanceId, name = name, loadTime = loadTime)
    }

    /**
     * Publishes the current screen unless the last load resolved to the same Activity instance under the same name.
     */
    private fun notifyLoad(
        instanceId: Int,
        name: String,
        loadTime: Long,
        screen: Screen = Screen.Named(name),
    ) {
        val notify = lastLoadName?.let { instanceId != lastLoadInstanceId || name != it } ?: true
        lastLoadInstanceId = instanceId
        lastLoadName = name

        if (notify) {
            eventBus.emitState(CurrentScreen.KEY, CurrentScreen(screen = screen, sinceMs = loadTime))
        }
    }

    private companion object {
        // Stands in for the Activity instance of a signal that names none, so that it takes part in de-duplication.
        const val NO_INSTANCE = 0
    }
}
