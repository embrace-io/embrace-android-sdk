package io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import io.embrace.android.embracesdk.internal.arch.lifecycle.ActivityLifecycle
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenChanged
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenSourceAttached
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.getId
import io.embrace.android.embracesdk.internal.arch.screen.ScreenSource
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStart
import io.embrace.android.embracesdk.internal.arch.screen.ScreenStop
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.utils.event.EventHandler
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * Discovers and attaches [NavController.OnDestinationChangedListener] instances to Activities that use a [NavController].
 * Implements [NavigationTrackingInitListener] so it can be registered with [NavigationTrackingService] tracking events.
 */
internal class NavControllerTracker(
    private val eventBus: EventBus,
    private val clock: Clock,
    private val errorHandler: ErrorHandler,
    private val controllerRef: (NavController) -> WeakReference<NavController> = ::WeakReference,
) : NavigationTrackingInitListener {

    /**
     * The NavController tracked for one Activity, and whether a screen has been started for its current destination.
     */
    private class TrackedController(
        val controller: WeakReference<NavController>,
        val activityId: Int,
    ) {
        /**
         * Guarded by this object, which is held while the screen's stop and start are emitted so that they can't interleave
         * with another thread's for the same controller.
         */
        var screenOpen = false
    }

    /**
     * An Activity's id is mapped to the first NavController found for it until it is removed, at which time
     * another NavController instance can be used if found.
     */
    private val trackedControllers = mutableMapOf<Int, TrackedController>()

    /**
     * Listeners registered on a [NavController] hold this reference rather than the tracker to allow
     * making listeners inert when the SDK is disabled.
     */
    private val trackerRef = AtomicReference(this)

    /**
     * Stops the screen of an Activity's current destination when the Activity is destroyed, as a best effort to pair its
     * last start. A destination otherwise stays open across its Activity pausing and resuming, bounded by the Activity's own
     * screen.
     */
    private val onActivityDestroyed = EventHandler<Activity> { activity ->
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            val tracked = synchronized(trackedControllers) { trackedControllers[activity.getId()] } ?: return@guardSdkCode
            synchronized(tracked) {
                if (tracked.screenOpen) {
                    tracked.screenOpen = false
                    stopScreen(tracked.activityId, clock.now(), ScreenStop.Reason.HIDDEN)
                }
            }
        }
    }

    fun onDataCaptureEnabled() {
        trackerRef.set(this)
        eventBus.addHandler(ActivityLifecycle.DESTROYED, onActivityDestroyed)
    }

    fun onDataCaptureDisabled() {
        trackerRef.set(null)
        eventBus.removeHandler(ActivityLifecycle.DESTROYED, onActivityDestroyed)
    }

    override fun trackNavigation(activity: Activity, controller: Any?) {
        if (trackerRef.get() == null) {
            return
        }
        errorHandler.guardSdkCode(SdkErrorType.NavControllerTrackingFail) {
            synchronized(trackedControllers) {
                val activityId = activity.getId()
                if (trackedControllers[activityId]?.controller?.get() != null) {
                    return@guardSdkCode
                }

                // Search for the first NavController of an Activity to track if an existing one isn't found.
                val navController = when (controller) {
                    null -> findNavController(activity)
                    is NavController -> controller
                    else -> null
                } ?: return@guardSdkCode
                trackedControllers.values.removeAll { it.controller.get() == null }
                val tracked = TrackedController(controllerRef(navController), activityId)
                trackedControllers[activityId] = tracked
                navController.trackForActivity(tracked)
            }
        }
    }

    private fun NavController.trackForActivity(tracked: TrackedController) {
        eventBus.emit(NavigationSignal.KEY, ScreenSourceAttached(tracked.activityId, clock.now()))
        val ref = trackerRef
        addOnDestinationChangedListener { _, destination, _ ->
            ref.get()?.onDestinationChanged(tracked, destination)
        }
    }

    private fun onDestinationChanged(tracked: TrackedController, destination: NavDestination) {
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            val activityId = tracked.activityId
            val name = extractScreenName(destination)
            val timestampMs = clock.now()
            eventBus.emit(NavigationSignal.KEY, ScreenChanged(activityId, name, timestampMs))
            synchronized(tracked) {
                if (tracked.screenOpen) {
                    stopScreen(activityId, timestampMs, ScreenStop.Reason.NAVIGATED)
                }
                tracked.screenOpen = true
                eventBus.emit(ScreenStart.KEY, ScreenStart(name, ScreenSource.NAVIGATION, activityId, timestampMs))
            }
        }
    }

    private fun stopScreen(activityId: Int, timestampMs: Long, reason: ScreenStop.Reason) {
        eventBus.emit(ScreenStop.KEY, ScreenStop(ScreenSource.NAVIGATION, activityId, timestampMs, reason))
    }

    private fun findNavController(activity: Activity): NavController? {
        if (activity is FragmentActivity) {
            val navHostFragment = activity.supportFragmentManager.fragments
                .firstNotNullOfOrNull { it as? NavHostFragment }
            if (navHostFragment != null) {
                return navHostFragment.navController
            }
        }
        return null
    }

    private fun extractScreenName(destination: NavDestination): String {
        return destination.route
            ?: destination.label?.toString()
            ?: destination.navigatorName
    }
}
