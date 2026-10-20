package io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceImpl
import io.embrace.android.embracesdk.internal.arch.limits.NoopLimitStrategy
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenChanged
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationSignal.ScreenSourceAttached
import io.embrace.android.embracesdk.internal.arch.navigation.getId

/**
 * Initializes androidx navigation tracking instrumentation
 */
public class AndroidxNavigationInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource> = {
        AndroidxNavigationDataSource(args)
    }
}

internal class AndroidxNavigationDataSource(
    private val args: InstrumentationArgs,
) : DataSourceImpl(
    args = args,
    limitStrategy = NoopLimitStrategy,
    instrumentationName = "androidx_navigation_data_source",
) {

    private val tracker = NavControllerTracker(args.eventBus, args.clock, args.errorHandler)
    private val trackNavigationHook: (Activity, Any?) -> Unit = tracker::trackNavigation

    private val attachBackStackHook = fun(activity: Activity) {
        args.eventBus.emit(NavigationSignal.KEY, ScreenSourceAttached(activity.getId(), args.clock.now()))
    }

    private val onBackStackDestinationChangeHook = fun(activity: Activity, newDestination: String) {
        args.eventBus.emit(NavigationSignal.KEY, ScreenChanged(activity.getId(), newDestination, args.clock.now()))
    }

    override fun onDataCaptureEnabled() {
        tracker.onDataCaptureEnabled()
        args.navigationTrackingService.navigationTrackingInitListener = tracker
        trackNavigation = trackNavigationHook
        attachBackStack = attachBackStackHook
        onBackStackDestinationChange = onBackStackDestinationChangeHook
    }

    override fun onDataCaptureDisabled() {
        tracker.onDataCaptureDisabled()

        // don't clobber hooks installed by a newer data source
        if (trackNavigation === trackNavigationHook) {
            trackNavigation = NOOP_TRACK_NAVIGATION
        }
        if (attachBackStack === attachBackStackHook) {
            attachBackStack = NOOP_ATTACH_BACK_STACK
        }
        if (onBackStackDestinationChange === onBackStackDestinationChangeHook) {
            onBackStackDestinationChange = NOOP_ON_BACK_STACK_DESTINATION_CHANGE
        }
    }
}

private val NOOP_TRACK_NAVIGATION: (Activity, Any?) -> Unit = { _, _ -> }
private val NOOP_ATTACH_BACK_STACK: (activity: Activity) -> Unit = { }
private val NOOP_ON_BACK_STACK_DESTINATION_CHANGE: (activity: Activity, newDestination: String) -> Unit = { _, _ -> }

/**
 * Function called by [io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedNavController]
 * to register a Nav 2 [androidx.navigation.NavController] with the SDK.
 */
@Volatile
internal var trackNavigation: (Activity, Any?) -> Unit = NOOP_TRACK_NAVIGATION
    private set

/**
 * Function called by [io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedBackStack]
 * when an Activity first attaches a Nav 3 back stack.
 */
@Volatile
internal var attachBackStack: (activity: Activity) -> Unit = NOOP_ATTACH_BACK_STACK
    private set

/**
 * Function called by [io.embrace.android.embracesdk.instrumentation.androidx.navigation.rememberObservedBackStack]
 * when the top-of-stack route changes for an attached Nav 3 back stack.
 */
@Volatile
internal var onBackStackDestinationChange: (activity: Activity, newDestination: String) -> Unit =
    NOOP_ON_BACK_STACK_DESTINATION_CHANGE
    private set
