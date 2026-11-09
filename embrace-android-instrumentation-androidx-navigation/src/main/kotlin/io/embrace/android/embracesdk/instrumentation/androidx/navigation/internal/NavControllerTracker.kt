package io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenChanged
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenSourceAttached
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.getId
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.utils.event.EventBus
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
     * An Activity's id is mapped to the first NavController found for it until it is removed, at which time
     * another NavController instance can be used if found.
     */
    private val trackedControllers = mutableMapOf<Int, WeakReference<NavController>>()

    /**
     * Listeners registered on a [NavController] hold this reference rather than the tracker to allow
     * making listeners inert when the SDK is disabled.
     */
    private val trackerRef = AtomicReference(this)

    fun onDataCaptureEnabled() {
        trackerRef.set(this)
    }

    fun onDataCaptureDisabled() {
        trackerRef.set(null)
    }

    override fun trackNavigation(activity: Activity, controller: Any?) {
        if (trackerRef.get() == null) {
            return
        }
        errorHandler.guardSdkCode(SdkErrorType.NavControllerTrackingFail) {
            synchronized(trackedControllers) {
                val activityId = activity.getId()
                if (trackedControllers[activityId]?.get() != null) {
                    return@guardSdkCode
                }

                // Search for the first NavController of an Activity to track if an existing one isn't found.
                val navController = when (controller) {
                    null -> findNavController(activity)
                    is NavController -> controller
                    else -> null
                } ?: return@guardSdkCode
                trackedControllers.values.removeAll { it.get() == null }
                trackedControllers[activityId] = controllerRef(navController)
                navController.trackForActivity(activity)
            }
        }
    }

    private fun NavController.trackForActivity(activity: Activity) {
        val activityId = activity.getId()
        eventBus.emit(NavigationSignal.KEY, ScreenSourceAttached(activityId, clock.now()))
        val ref = trackerRef
        addOnDestinationChangedListener { _, destination, _ ->
            ref.get()?.onDestinationChanged(activityId, destination)
        }
    }

    private fun onDestinationChanged(activityId: Int, destination: NavDestination) {
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) {
            eventBus.emit(NavigationSignal.KEY, ScreenChanged(activityId, extractScreenName(destination), clock.now()))
        }
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
