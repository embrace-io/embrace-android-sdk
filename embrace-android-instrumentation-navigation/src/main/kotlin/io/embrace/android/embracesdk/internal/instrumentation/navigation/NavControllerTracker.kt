package io.embrace.android.embracesdk.internal.instrumentation.navigation

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger

/**
 * Discovers and attaches [NavController.OnDestinationChangedListener]s to Activities that use a [NavController] to control navigation.
 *
 * If an instance is found to not contain a [NavController], it will not be retried.
 */
internal class NavControllerTracker(
    private val onEvent: (NavigationEvent) -> Unit,
    private val clock: Clock,
    private val logger: InternalLogger,
) {
    private val processedActivities = mutableSetOf<Int>()

    fun track(activity: Activity) {
        runCatching {
            val processActivity = synchronized(processedActivities) {
                processedActivities.add(activity.getId())
            }

            if (processActivity) {
                findNavController(activity)?.apply {
                    onEvent(NavigationEvent.NavControllerAttached(activity, clock.now()))
                    addOnDestinationChangedListener { _, destination, _ ->
                        onEvent(NavigationEvent.NavControllerDestinationChanged(activity, extractScreenName(destination), clock.now()))
                    }
                }
            }
        }.onFailure {
            logger.trackInternalError(InternalErrorType.NAV_CONTROLLER_TRACKING_FAIL, it)
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
