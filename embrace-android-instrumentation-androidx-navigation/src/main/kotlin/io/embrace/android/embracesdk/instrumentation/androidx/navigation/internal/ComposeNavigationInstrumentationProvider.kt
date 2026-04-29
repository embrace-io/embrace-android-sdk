package io.embrace.android.embracesdk.instrumentation.androidx.navigation.internal

import android.app.Activity
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService

/**
 * Instrumentation provider that creates and registers [NavControllerTracker] with the [NavigationTrackingService].
 */
public class ComposeNavigationInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        args.navigationTrackingService.navigationTrackingInitListener =
            NavControllerTracker(args.navigationTrackingService, args.clock, args.logger)
        trackNavigation = args.navigationTrackingService::trackNavigation
        return null
    }
}

/**
 * Function to call to track a component that controls navigation
 */
internal var trackNavigation: (Activity, Any?) -> Unit = { _, _ -> }
    private set
