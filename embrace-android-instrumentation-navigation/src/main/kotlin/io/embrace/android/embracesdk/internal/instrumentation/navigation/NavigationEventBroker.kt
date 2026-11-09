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
    private val pausedActivities = mutableSetOf<Int>()

    private var lastLoadInstanceId = NO_INSTANCE
    private var lastLoadScreen: Screen? = null

    @UiThread
    override fun onEvent(event: NavigationSignal) {
        when (event) {
            is NavigationSignal.ActivityStarted -> {
                activityStartTimes[event.instanceId] = event.timestampMs
            }

            is NavigationSignal.ActivityResumed -> handleActivityResumed(event)

            is NavigationSignal.ActivityPaused -> {
                visibleScreens.remove(event.instanceId)
                pausedActivities.add(event.instanceId)
            }

            // the state kept for an Activity goes with it, as its id is never seen again
            is NavigationSignal.ActivityDestroyed -> {
                activityStartTimes.remove(event.instanceId)
                visibleScreens.remove(event.instanceId)
                lastScreenSourceScreens.remove(event.instanceId)
                pausedActivities.remove(event.instanceId)
            }

            is NavigationSignal.ScreenSourceAttached -> {
                lastScreenSourceScreens[event.instanceId] = Screen.NavControllerInitializing
            }

            is NavigationSignal.ScreenChanged -> {
                val screen = Screen.Named(event.name)
                lastScreenSourceScreens[event.instanceId] = screen
                // a paused Activity isn't the screen shown, as when another Activity covers it or the app is in the background,
                // so its screen change is kept for when it resumes
                if (event.instanceId in pausedActivities) {
                    return
                }
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
        // An Activity that was only paused, as when a translucent Activity covered it, resumes without starting again, so it
        // became visible again when it resumed. De-duplication drops a resume that doesn't change the screen.
        val startTime = activityStartTimes.remove(event.instanceId) ?: event.timestampMs
        pausedActivities.remove(event.instanceId)

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
            // If the Activity has a screen source but there isn't a screen visible, it was paused, as when the app went to the
            // background or another Activity covered it. So we make its last screen visible, including one it changed to while
            // paused, and notify about the screen load, using the start time, or the resume time if it only paused, as the load time.
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
     * Publishes the current screen unless the last load resolved to the same screen of the same Activity instance. The screen
     * rather than the signal's name is compared, as an Activity with a screen source resumes under its own name with whichever
     * screen it last showed.
     */
    private fun notifyLoad(
        instanceId: Int,
        name: String,
        loadTime: Long,
        screen: Screen = Screen.Named(name),
    ) {
        val notify = lastLoadScreen?.let { instanceId != lastLoadInstanceId || screen != it } ?: true
        lastLoadInstanceId = instanceId
        lastLoadScreen = screen

        if (notify) {
            eventBus.emitState(CurrentScreen.KEY, CurrentScreen(screen = screen, sinceMs = loadTime))
        }
    }

    private companion object {
        // Stands in for the Activity instance of a signal that names none, so that it takes part in de-duplication.
        const val NO_INSTANCE = 0
    }
}
