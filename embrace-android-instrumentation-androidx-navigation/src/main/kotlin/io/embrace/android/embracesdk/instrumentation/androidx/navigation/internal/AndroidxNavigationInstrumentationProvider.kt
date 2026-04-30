package io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

/**
 * Initializes androidx navigation tracking instrumentation
 */
public class AndroidxNavigationInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        val navigationControllerEventListener = args.navigationTrackingService
        NavControllerTracker(navigationControllerEventListener, args.clock, args.logger).apply {
            args.navigationTrackingService.navigationTrackingInitListener = this
            trackNavigation = ::trackNavigation
        }

        with(args) {
            attachBackStack = fun(activity: Activity) {
                navigationTrackingService.onControllerAttached(activity, clock.now())
            }
            onBackStackDestinationChange = fun(activity: Activity, newDestination: String) {
                navigationTrackingService.onDestinationChange(activity, newDestination, clock.now())
            }
        }
        return null
    }
}

/**
 * Function called by [io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedNavController]
 * to register a Nav 2 [androidx.navigation.NavController] with the SDK.
 */
internal var trackNavigation: (Activity, Any?) -> Unit = { _, _ -> }
    private set

/**
 * Function called by [io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedBackStack]
 * when an Activity first attaches a Nav 3 back stack.
 */
internal var attachBackStack: (activity: Activity) -> Unit = { }
    private set

/**
 * Function called by [io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedBackStack]
 * when the top-of-stack route changes for an attached Nav 3 back stack.
 */
internal var onBackStackDestinationChange: (activity: Activity, newDestination: String) -> Unit = { _, _ -> }
    private set
