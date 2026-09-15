package io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenChanged
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenSourceAttached
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingInitListener
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService
import io.embrace.android.embracesdk.internal.arch.navigation.getId
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.utils.event.EventBus

/**
 * Discovers and attaches [NavController.OnDestinationChangedListener] instances to Activities that use a [NavController].
 * Implements [NavigationTrackingInitListener] so it can be registered with [NavigationTrackingService] tracking events.
 */
internal class NavControllerTracker(
    private val eventBus: EventBus,
    private val clock: Clock,
    private val logger: InternalLogger,
) : NavigationTrackingInitListener {

    private val trackAttemptStatus = mutableMapOf<Int, Boolean>()

    override fun trackNavigation(activity: Activity, controller: Any?) {
        runCatching {
            val activityId = activity.getId()
            if (trackAttemptStatus[activityId] != true) {
                synchronized(trackAttemptStatus) {
                    if (controller == null && trackAttemptStatus.put(activityId, false) == null) {
                        findNavController(activity)?.trackForActivity(activity)
                    } else if (controller is NavController && trackAttemptStatus[activityId] != true) {
                        controller.trackForActivity(activity)
                    }
                }
            }
        }.onFailure {
            logger.trackInternalError(InternalErrorType.NavControllerTrackingFail, it)
        }
    }

    private fun NavController.trackForActivity(activity: Activity) {
        val activityId = activity.getId()
        eventBus.emit(ScreenSourceAttached(activityId, clock.now()))
        addOnDestinationChangedListener { _, destination, _ ->
            eventBus.emit(ScreenChanged(activityId, extractScreenName(destination), clock.now()))
        }
        trackAttemptStatus[activityId] = true
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
