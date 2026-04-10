package io.embrace.android.embracesdk.internal.instrumentation.compose.navigation

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.navigation.NavigationTrackingService

/**
 * Instrumentation provider that creates and registers [NavControllerTracker] with the [NavigationTrackingService].
 */
class ComposeNavigationInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        val tracker = NavControllerTracker(args.navigationTrackingService, args.clock, args.logger)
        args.navigationTrackingService.setTrackingInitListener(tracker)
        return null
    }
}
